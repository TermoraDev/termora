package app.termora.plugins.obs

import app.termora.DynamicIcon
import app.termora.Icons
import app.termora.protocol.FileObjectHandler
import app.termora.protocol.FileObjectRequest
import app.termora.protocol.TransferProtocolProvider
import org.apache.commons.vfs2.provider.FileProvider

class OBSProtocolProvider private constructor() : TransferProtocolProvider {

    companion object {
        val instance by lazy { OBSProtocolProvider() }
        const val PROTOCOL = "OBS"
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon {
        return Icons.huawei
    }

    override fun getFileProvider(): FileProvider {
        return OBSFileProvider.instance
    }

    override fun getRootFileObject(requester: FileObjectRequest): FileObjectHandler {
        TODO("Not yet implemented")
    }

}