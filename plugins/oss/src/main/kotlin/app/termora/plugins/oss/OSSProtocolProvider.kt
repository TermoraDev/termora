package app.termora.plugins.oss

import app.termora.DynamicIcon
import app.termora.Icons
import app.termora.protocol.FileObjectHandler
import app.termora.protocol.FileObjectRequest
import app.termora.protocol.TransferProtocolProvider
import org.apache.commons.vfs2.provider.FileProvider

class OSSProtocolProvider private constructor() : TransferProtocolProvider {

    companion object {
        val instance by lazy { OSSProtocolProvider() }
        const val PROTOCOL = "OSS"
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon {
        return Icons.aliyun
    }

    override fun getFileProvider(): FileProvider {
        return OSSFileProvider.instance
    }

    override fun getRootFileObject(requester: FileObjectRequest): FileObjectHandler {
        TODO("Not yet implemented")
    }

}