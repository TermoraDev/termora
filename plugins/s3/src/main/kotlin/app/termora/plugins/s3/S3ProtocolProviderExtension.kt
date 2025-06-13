package app.termora.plugins.s3

import app.termora.protocol.ProtocolProvider
import app.termora.protocol.ProtocolProviderExtension

class S3ProtocolProviderExtension private constructor() : ProtocolProviderExtension {
    companion object {
        val instance by lazy { S3ProtocolProviderExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return S3ProtocolProvider.Companion.instance
    }
}