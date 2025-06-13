package app.termora.plugins.ftp

import app.termora.DynamicIcon
import app.termora.Icons
import app.termora.protocol.FileObjectHandler
import app.termora.protocol.FileObjectRequest
import app.termora.protocol.TransferProtocolProvider
import org.apache.commons.vfs2.provider.FileProvider

class FTPProtocolProvider private constructor() : TransferProtocolProvider {

    companion object {
        val instance by lazy { FTPProtocolProvider() }
        const val PROTOCOL = "FTP"
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon {
        return Icons.ftp
    }

    override fun getFileProvider(): FileProvider {
        return FTPFileProvider.instance
    }

    override fun getRootFileObject(requester: FileObjectRequest): FileObjectHandler {
        TODO("Not yet implemented")
    }

}