package app.termora.plugins.cos

import app.termora.DynamicIcon
import app.termora.Icons
import app.termora.protocol.FileObjectHandler
import app.termora.protocol.FileObjectRequest
import app.termora.protocol.TransferProtocolProvider
import org.apache.commons.vfs2.provider.FileProvider

class COSProtocolProvider private constructor() : TransferProtocolProvider {

    companion object {
        val instance by lazy { COSProtocolProvider() }
        const val PROTOCOL = "COS"
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon {
        return Icons.tencent
    }

    override fun getFileProvider(): FileProvider {
        return COSFileProvider.instance
    }

    override fun getRootFileObject(requester: FileObjectRequest): FileObjectHandler {
        TODO("Not yet implemented")
    }

}