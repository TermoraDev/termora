package app.termora.plugins.oss

import app.termora.protocol.ProtocolProvider
import app.termora.protocol.ProtocolProviderExtension

class OSSProtocolProviderExtension private constructor() : ProtocolProviderExtension {
    companion object {
        val instance by lazy { OSSProtocolProviderExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return OSSProtocolProvider.Companion.instance
    }
}