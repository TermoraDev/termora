package app.termora.plugins.ftp

import app.termora.transfer.s3.S3FileSystem
import app.termora.transfer.s3.S3Path
import org.apache.commons.io.IOUtils
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.pool2.impl.GenericObjectPool

class FTPFileSystem(private val pool: GenericObjectPool<FTPClient>) : S3FileSystem(FTPSystemProvider(pool)) {

    override fun create(root: String?, names: List<String>): S3Path {
        val path = FTPPath(this, root, names)
        if (names.isEmpty()) {
            path.attributes = path.attributes.copy(directory = true)
        }
        return path
    }

    override fun close() {
        IOUtils.closeQuietly(pool)
        super.close()
    }
}
