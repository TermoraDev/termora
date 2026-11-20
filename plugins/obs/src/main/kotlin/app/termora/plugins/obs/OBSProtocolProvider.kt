package app.termora.plugins.obs

import app.termora.DynamicIcon
import app.termora.Icons
import app.termora.protocol.PathHandler
import app.termora.protocol.PathHandlerRequest
import app.termora.protocol.TransferProtocolProvider
import com.obs.services.BasicObsCredentialsProvider
import com.obs.services.model.ListBucketsRequest


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

    override fun createPathHandler(requester: PathHandlerRequest): PathHandler {
        val host = requester.host
        val accessKeyId = host.username
        val secretAccessKey = host.authentication.password

        val cred = BasicObsCredentialsProvider(accessKeyId, secretAccessKey)
        val endpoint = host.host.ifBlank { "https://obs.cn-north-4.myhuaweicloud.com" }
        val obsClient = OBSClientHandler.createOBSClient(cred, endpoint, host.proxy)
        val buckets = try {
            obsClient.listBuckets(ListBucketsRequest())
        } finally {
            obsClient.close()
        }
        val defaultPath = host.options.sftpDefaultDirectory
        val fs = OBSFileSystem(OBSClientHandler(cred, host.proxy, buckets, host.host))
        return PathHandler(fs, fs.getPath(defaultPath))

    }


}
