package app.termora.plugins.obs

import app.termora.protocol.ProtocolProvider
import app.termora.protocol.ProtocolProviderExtension

class OBSProtocolProviderExtension private constructor() : ProtocolProviderExtension {
    companion object {
        val instance by lazy { OBSProtocolProviderExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return OBSProtocolProvider.Companion.instance
    }
}