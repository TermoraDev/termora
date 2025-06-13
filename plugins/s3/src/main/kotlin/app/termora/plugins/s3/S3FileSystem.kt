package app.termora.plugins.s3

import io.minio.MinioClient
import org.apache.commons.vfs2.Capability
import org.apache.commons.vfs2.FileName
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.FileSystemOptions
import org.apache.commons.vfs2.provider.AbstractFileName
import org.apache.commons.vfs2.provider.AbstractFileSystem

class S3FileSystem(
    private val minio: MinioClient,
    rootName: FileName,
    fileSystemOptions: FileSystemOptions
) : AbstractFileSystem(rootName, null, fileSystemOptions) {

    override fun addCapabilities(caps: MutableCollection<Capability>) {
        caps.addAll(S3FileProvider.capabilities)
    }

    override fun createFile(name: AbstractFileName): FileObject? {
        return S3FileObject(minio, name, this)
    }

    fun getDelimiter(): String {
        return S3FileSystemConfigBuilder.instance.getDelimiter(fileSystemOptions)
    }

    override fun close() {
        minio.close()
    }
}