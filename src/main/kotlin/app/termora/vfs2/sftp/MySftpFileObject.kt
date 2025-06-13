package app.termora.vfs2.sftp

import app.termora.sftp.FileSystemViewTableModel
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.FileSystemException
import org.apache.commons.vfs2.FileType
import org.apache.commons.vfs2.provider.AbstractFileName
import org.apache.commons.vfs2.provider.AbstractFileObject
import org.apache.sshd.sftp.client.SftpClient
import org.apache.sshd.sftp.client.fs.SftpFileSystem
import org.apache.sshd.sftp.client.fs.SftpPath
import org.apache.sshd.sftp.client.fs.WithFileAttributes
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.*

internal class MySftpFileObject(
    private val sftpFileSystem: SftpFileSystem,
    fileName: AbstractFileName,
    fileSystem: MySftpFileSystem
) : AbstractFileObject<MySftpFileSystem>(fileName, fileSystem) {

    companion object {
        private val log = LoggerFactory.getLogger(MySftpFileObject::class.java)

        const val POSIX_FILE_PERMISSIONS = "PosixFilePermissions"
    }

    private var _attributes: SftpClient.Attributes? = null
    private val isInitialized = AtomicBoolean(false)
    private val path by lazy { sftpFileSystem.getPath(fileName.path) }
    private val attributes = mutableMapOf<String, Any>()

    override fun doGetContentSize(): Long {
        val attributes = getAttributes()
        if (attributes == null || !attributes.flags.contains(SftpClient.Attribute.Size)) {
            throw FileSystemException("vfs.provider.sftp/unknown-size.error")
        }
        return attributes.size
    }

    override fun doGetType(): FileType {
        val attributes = getAttributes() ?: return FileType.IMAGINARY
        return if (attributes.isDirectory)
            FileType.FOLDER
        else if (attributes.isRegularFile)
            FileType.FILE
        else if (attributes.isSymbolicLink) {
            val e = path.readSymbolicLink()
            if (e is SftpPath && e.attributes != null) {
                if (e.attributes.isDirectory) {
                    FileType.FOLDER
                } else {
                    FileType.FILE
                }
            } else if (e.isDirectory()) {
                FileType.FOLDER
            } else {
                FileType.FILE
            }
        } else FileType.IMAGINARY
    }

    override fun doListChildren(): Array<String>? {
        return null
    }

    override fun doListChildrenResolved(): Array<FileObject>? {
        if (isFile) return null

        val children = mutableListOf<FileObject>()

        Files.list(path).use { files ->
            for (file in files) {
                val fo = resolveFile(file.name)
                if (file is WithFileAttributes && fo is MySftpFileObject) {
                    if (fo.isInitialized.compareAndSet(false, true)) {
                        fo.setAttributes(file.attributes)
                    }
                }
                children.add(fo)
            }
        }

        return children.toTypedArray()
    }

    override fun doGetOutputStream(bAppend: Boolean): OutputStream {
        if (bAppend) {
            return path.outputStream(StandardOpenOption.WRITE, StandardOpenOption.APPEND)
        }
        return path.outputStream()
    }

    override fun doGetInputStream(bufferSize: Int): InputStream {
        return path.inputStream()
    }

    override fun doCreateFolder() {
        Files.createDirectories(path)
    }

    override fun doIsExecutable(): Boolean {
        val permissions = getPermissions()
        return permissions.contains(PosixFilePermission.GROUP_EXECUTE) ||
                permissions.contains(PosixFilePermission.OWNER_EXECUTE) ||
                permissions.contains(PosixFilePermission.GROUP_EXECUTE)
    }

    override fun doIsReadable(): Boolean {
        val permissions = getPermissions()
        return permissions.contains(PosixFilePermission.GROUP_READ) ||
                permissions.contains(PosixFilePermission.OWNER_READ) ||
                permissions.contains(PosixFilePermission.OTHERS_READ)
    }

    override fun doIsWriteable(): Boolean {
        val permissions = getPermissions()
        return permissions.contains(PosixFilePermission.GROUP_WRITE) ||
                permissions.contains(PosixFilePermission.OWNER_WRITE) ||
                permissions.contains(PosixFilePermission.OTHERS_WRITE)
    }

    override fun doRename(newFile: FileObject) {
        if (newFile !is MySftpFileObject) {
            throw FileSystemException("vfs.provider/rename-not-supported.error")
        }
        Files.move(path, newFile.path, StandardCopyOption.ATOMIC_MOVE)
    }

    override fun moveTo(destFile: FileObject) {
        if (canRenameTo(destFile)) {
            doRename(destFile)
        } else {
            throw FileSystemException("vfs.provider/rename-not-supported.error")
        }
    }

    override fun doDelete() {
        sftpFileSystem.client.use { deleteRecursivelySFTP(path, it) }
    }

    /**
     * 优化删除效率，采用一个连接
     */
    private fun deleteRecursivelySFTP(path: SftpPath, sftpClient: SftpClient) {
        val isDirectory = if (path.attributes != null) path.attributes.isDirectory else path.isDirectory()
        if (isDirectory) {
            for (e in sftpClient.readDir(path.toString())) {
                if (e.filename == ".." || e.filename == ".") {
                    continue
                }
                if (e.attributes.isDirectory) {
                    deleteRecursivelySFTP(path.resolve(e.filename), sftpClient)
                } else {
                    sftpClient.remove(path.resolve(e.filename).toString())
                }
            }
            sftpClient.rmdir(path.toString())
        } else {
            sftpClient.remove(path.toString())
        }

    }

    override fun doSetExecutable(executable: Boolean, ownerOnly: Boolean): Boolean {
        val permissions = getPermissions().toMutableSet()
        permissions.add(PosixFilePermission.OWNER_EXECUTE)
        if (ownerOnly) {
            permissions.remove(PosixFilePermission.OTHERS_EXECUTE)
            permissions.remove(PosixFilePermission.GROUP_EXECUTE)
        }
        Files.setPosixFilePermissions(path, permissions)
        return true
    }

    override fun doSetReadable(readable: Boolean, ownerOnly: Boolean): Boolean {
        val permissions = getPermissions().toMutableSet()
        permissions.add(PosixFilePermission.OWNER_READ)
        if (ownerOnly) {
            permissions.remove(PosixFilePermission.OTHERS_READ)
            permissions.remove(PosixFilePermission.GROUP_EXECUTE)
        }
        Files.setPosixFilePermissions(path, permissions)
        return true
    }

    override fun doSetWritable(writable: Boolean, ownerOnly: Boolean): Boolean {
        val permissions = getPermissions().toMutableSet()
        permissions.add(PosixFilePermission.OWNER_WRITE)
        if (ownerOnly) {
            permissions.remove(PosixFilePermission.OTHERS_WRITE)
            permissions.remove(PosixFilePermission.GROUP_WRITE)
        }
        Files.setPosixFilePermissions(path, permissions)
        return true
    }

    override fun doSetLastModifiedTime(modtime: Long): Boolean {
        Files.setLastModifiedTime(path, FileTime.fromMillis(modtime))
        return true
    }

    override fun doDetach() {
        setAttributes(null)
        isInitialized.compareAndSet(true, false)
    }

    override fun doIsHidden(): Boolean {
        return name.baseName.startsWith(".")
    }

    override fun doGetAttributes(): MutableMap<String, Any> {
        return attributes
    }

    override fun doGetLastModifiedTime(): Long {
        val attributes = getAttributes()
        if (attributes == null || !attributes.flags.contains(SftpClient.Attribute.ModifyTime)) {
            throw FileSystemException("vfs.provider.sftp/unknown-modtime.error")
        }
        return attributes.modifyTime.toMillis()
    }

    override fun doSetAttribute(attrName: String, value: Any) {
        attributes[attrName] = value
    }

    override fun doIsSymbolicLink(): Boolean {
        return getAttributes()?.isSymbolicLink == true
    }

    fun setPosixFilePermissions(permissions: Set<PosixFilePermission>) {
        path.setPosixFilePermissions(permissions)
    }

    private fun getAttributes(): SftpClient.Attributes? {
        if (isInitialized.compareAndSet(false, true)) {
            try {
                val attributes = sftpFileSystem.provider()
                    .readRemoteAttributes(sftpFileSystem.provider().toSftpPath(path))
                setAttributes(attributes)
            } catch (e: Exception) {
                if (log.isDebugEnabled) {
                    log.debug(e.message, e)
                }
            }
        }
        return _attributes
    }

    private fun setAttributes(attributes: SftpClient.Attributes?) {
        if (attributes == null) {
            doGetAttributes().remove(POSIX_FILE_PERMISSIONS)
        } else {
            doSetAttribute(POSIX_FILE_PERMISSIONS, attributes.permissions)
        }
        this._attributes = attributes
    }

    private fun getPermissions(): Set<PosixFilePermission> {
        return FileSystemViewTableModel.fromSftpPermissions(getAttributes()?.permissions ?: return setOf())
    }


}