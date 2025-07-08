package app.termora.plugins.ftp

import app.termora.transfer.s3.S3Path

class FTPPath(fileSystem: FTPFileSystem, root: String?, names: List<String>) : S3Path(fileSystem, root, names) {
    override val isBucket: Boolean
        get() = false

    override val bucketName: String
        get() = throw UnsupportedOperationException()

    override val objectName: String
        get() = throw UnsupportedOperationException()

    override fun getCustomType(): String? {
        return null
    }


}