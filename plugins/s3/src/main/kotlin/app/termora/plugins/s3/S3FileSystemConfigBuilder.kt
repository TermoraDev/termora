package app.termora.plugins.s3

import app.termora.vfs2.s3.AbstractS3FileSystemConfigBuilder
import org.apache.commons.vfs2.FileSystem

class S3FileSystemConfigBuilder private constructor() : AbstractS3FileSystemConfigBuilder() {
    companion object {
        val instance by lazy { S3FileSystemConfigBuilder() }
    }

    override fun getConfigClass(): Class<out FileSystem> {
        return S3FileSystem::class.java
    }
}