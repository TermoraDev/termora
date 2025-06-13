package app.termora.plugins.s3

import io.minio.MinioClient
import org.apache.commons.vfs2.Capability
import org.apache.commons.vfs2.FileName
import org.apache.commons.vfs2.FileSystem
import org.apache.commons.vfs2.FileSystemOptions
import org.apache.commons.vfs2.provider.AbstractOriginatingFileProvider

class S3FileProvider private constructor() : AbstractOriginatingFileProvider() {

    companion object {
        val instance by lazy { S3FileProvider() }
        val capabilities = listOf(
            Capability.CREATE,
            Capability.DELETE,
            Capability.RENAME,
            Capability.GET_TYPE,
            Capability.LIST_CHILDREN,
            Capability.READ_CONTENT,
            Capability.WRITE_CONTENT,
            Capability.GET_LAST_MODIFIED,
            Capability.RANDOM_ACCESS_READ,
        )
    }

    override fun getCapabilities(): Collection<Capability> {
        return S3FileProvider.capabilities
    }

    override fun doCreateFileSystem(
        rootFileName: FileName,
        options: FileSystemOptions
    ): FileSystem {
        val region = S3FileSystemConfigBuilder.instance.getRegion(options)
        val endpoint = S3FileSystemConfigBuilder.instance.getEndpoint(options)
        val accessKey = S3FileSystemConfigBuilder.instance.getAccessKey(options)
        val secretKey = S3FileSystemConfigBuilder.instance.getSecretKey(options)
        val builder = MinioClient.builder()
        builder.endpoint(endpoint)
        builder.credentials(accessKey, secretKey)
        if (region.isNotBlank()) builder.region(region)
        return S3FileSystem(builder.build(), rootFileName, options)
    }


}