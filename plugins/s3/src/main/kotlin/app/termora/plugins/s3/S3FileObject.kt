package app.termora.plugins.s3

import app.termora.DynamicIcon
import app.termora.Icons
import app.termora.vfs2.FileObjectDescriptor
import io.minio.*
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.FileType
import org.apache.commons.vfs2.provider.AbstractFileName
import org.apache.commons.vfs2.provider.AbstractFileObject
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

class S3FileObject(
    private val minio: MinioClient,
    fileName: AbstractFileName,
    fileSystem: S3FileSystem
) : AbstractFileObject<S3FileSystem>(fileName, fileSystem), FileObjectDescriptor {
    private var attributes = Attributes()

    init {
        attributes = attributes.copy(isRoot = name.path == fileSystem.getDelimiter())
    }

    override fun doGetContentSize(): Long {
        return attributes.size
    }

    override fun doGetType(): FileType {
        return if (attributes.isRoot || attributes.isBucket) FileType.FOLDER
        else if (attributes.isDirectory && attributes.isFile) FileType.FILE_OR_FOLDER
        else if (attributes.isFile) FileType.FILE
        else if (attributes.isDirectory) FileType.FOLDER
        else FileType.IMAGINARY
    }

    override fun doListChildren(): Array<out String?>? {
        return null
    }

    override fun doCreateFolder() {
        // Nothing
    }

    private fun getBucketName(): String {
        if (StringUtils.isNotBlank(attributes.bucket)) {
            return attributes.bucket
        }
        if (parent is S3FileObject) {
            return (parent as S3FileObject).getBucketName()
        }
        throw IllegalArgumentException("Bucket must be a S3 file object")
    }

    override fun doListChildrenResolved(): Array<FileObject>? {
        if (isFile) return null

        val children = mutableListOf<FileObject>()

        if (attributes.isRoot) {
            val buckets = minio.listBuckets()
            for (bucket in buckets) {
                val file = resolveFile(bucket.name())
                if (file is S3FileObject) {
                    file.attributes = file.attributes.copy(
                        isBucket = true,
                        bucket = bucket.name(),
                        isDirectory = false,
                        isFile = false,
                        lastModified = bucket.creationDate().toInstant().toEpochMilli()
                    )
                    children.add(file)
                }
            }
        } else if (attributes.isBucket || attributes.isDirectory) {
            val builder = ListObjectsArgs.builder().bucket(getBucketName())
                .delimiter(fileSystem.getDelimiter())
            var prefix = StringUtils.EMPTY
            if (attributes.isDirectory) {
                // remove first delimiter
                prefix = StringUtils.removeStart(name.path, fileSystem.getDelimiter())
                // remove bucket
                prefix = StringUtils.removeStart(prefix, getBucketName())
                // remove first delimiter
                prefix = StringUtils.removeStart(prefix, fileSystem.getDelimiter())
                // remove last delimiter
                prefix = StringUtils.removeEnd(prefix, fileSystem.getDelimiter())
                prefix = prefix + fileSystem.getDelimiter()
            }
            builder.prefix(prefix)

            for (e in minio.listObjects(builder.build())) {
                val item = e.get()
                val objectName = StringUtils.removeStart(item.objectName(), prefix)
                val file = resolveFile(objectName)
                if (file is S3FileObject) {
                    val lastModified = if (item.lastModified() != null) item.lastModified()
                        .toInstant().toEpochMilli() else 0
                    val owner = if (item.owner() != null) item.owner().displayName() else StringUtils.EMPTY
                    file.attributes = file.attributes.copy(
                        bucket = attributes.bucket,
                        isDirectory = item.isDir,
                        isFile = item.isDir.not(),
                        lastModified = lastModified,
                        size = if (item.isDir.not()) item.size() else 0,
                        owner = owner
                    )
                    children.add(file)
                }
            }

        }

        return children.toTypedArray()
    }

    override fun getFileSystem(): S3FileSystem {
        return super.getFileSystem() as S3FileSystem
    }

    override fun doGetLastModifiedTime(): Long {
        return attributes.lastModified
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon? {
        if (attributes.isBucket) {
            return Icons.dbms
        }
        return super.getIcon(width, height)
    }

    override fun getTypeDescription(): String? {
        if (attributes.isBucket) {
            return "Bucket"
        }
        return null
    }

    override fun getLastModified(): Long? {
        return attributes.lastModified
    }

    override fun getOwner(): String? {
        return attributes.owner
    }

    override fun doDelete() {
        if (isFile) {
            minio.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(getBucketName()).`object`(getObjectName()).build()
            )
        }
    }

    override fun doGetOutputStream(bAppend: Boolean): OutputStream? {
        return createStreamer()
    }

    private fun createStreamer(): OutputStream {
        val pis = PipedInputStream()
        val pos = PipedOutputStream(pis)

        val thread = Thread.ofVirtual().start {
            minio.putObject(
                PutObjectArgs.builder()
                    .bucket(getBucketName())
                    .stream(pis, -1, 32 * 1024 * 1024)
                    .`object`(getObjectName()).build()
            )
            IOUtils.closeQuietly(pis)
        }

        return object : OutputStream() {
            override fun write(b: Int) {
                pos.write(b)
            }

            override fun close() {
                pos.close()
                thread.join()
            }
        }
    }

    override fun doGetInputStream(bufferSize: Int): InputStream? {
        return minio.getObject(GetObjectArgs.builder().bucket(getBucketName()).`object`(getObjectName()).build())
    }

    private fun getObjectName(): String {
        var objectName = StringUtils.removeStart(name.path, fileSystem.getDelimiter())
        objectName = StringUtils.removeStart(objectName, getBucketName())
        objectName = StringUtils.removeStart(objectName, fileSystem.getDelimiter())
        return objectName
    }

    private data class Attributes(
        val isRoot: Boolean = false,
        val isBucket: Boolean = false,
        val isDirectory: Boolean = false,
        val isFile: Boolean = false,
        /**
         * 只要不是 root 那么一定存在 bucket
         */
        val bucket: String = StringUtils.EMPTY,
        /**
         * 最后修改时间
         */
        val lastModified: Long = 0,
        /**
         * 文件大小
         */
        val size: Long = 0,
        /**
         * 所有者
         */
        val owner: String = StringUtils.EMPTY
    )
}