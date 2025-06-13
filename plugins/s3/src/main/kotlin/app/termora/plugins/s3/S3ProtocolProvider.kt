package app.termora.plugins.s3

import app.termora.DynamicIcon
import app.termora.Icons
import app.termora.protocol.FileObjectHandler
import app.termora.protocol.FileObjectRequest
import app.termora.protocol.TransferProtocolProvider
import io.minio.MinioClient
import org.apache.commons.lang3.StringUtils
import org.apache.commons.vfs2.FileSystemOptions
import org.apache.commons.vfs2.VFS
import org.apache.commons.vfs2.provider.FileProvider

class S3ProtocolProvider private constructor() : TransferProtocolProvider {

    companion object {
        val instance by lazy { S3ProtocolProvider() }
        const val PROTOCOL = "S3"
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon {
        return Icons.minio
    }

    override fun getFileProvider(): FileProvider {
        return S3FileProvider.instance
    }

    override fun getRootFileObject(requester: FileObjectRequest): FileObjectHandler {
        val host = requester.host
        val builder = MinioClient.builder()
            .endpoint(host.host)
            .credentials(host.username, host.authentication.password)
        val region = host.options.extras["s3.region"]
        if (StringUtils.isNotBlank(region)) {
            builder.region(region)
        }
        val delimiter = host.options.extras["s3.delimiter"] ?: "/"
        val options = FileSystemOptions()
        val defaultPath = host.options.sftpDefaultDirectory

        S3FileSystemConfigBuilder.instance.setRegion(options, StringUtils.defaultString(region))
        S3FileSystemConfigBuilder.instance.setEndpoint(options, host.host)
        S3FileSystemConfigBuilder.instance.setAccessKey(options, host.username)
        S3FileSystemConfigBuilder.instance.setSecretKey(options, host.authentication.password)
        S3FileSystemConfigBuilder.instance.setDelimiter(options, delimiter)

        val file = VFS.getManager().resolveFile(
            "s3://${StringUtils.defaultIfBlank(defaultPath, "/")}",
            options
        )
        return FileObjectHandler(file)
    }

}