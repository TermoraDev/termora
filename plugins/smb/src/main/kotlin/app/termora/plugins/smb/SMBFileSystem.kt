package app.termora.plugins.smb

import app.termora.transfer.s3.S3FileSystem
import app.termora.transfer.s3.S3Path
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare

class SMBFileSystem(private val share: DiskShare, session: Session) :
    S3FileSystem(SMBFileSystemProvider(share, session)) {

    override fun create(root: String?, names: List<String>): S3Path {
        val path = SMBPath(this, root, names)
        if (names.isEmpty()) {
            path.attributes = path.attributes.copy(directory = true)
        }
        return path
    }


    override fun close() {
        share.close()
        super.close()
    }

}
