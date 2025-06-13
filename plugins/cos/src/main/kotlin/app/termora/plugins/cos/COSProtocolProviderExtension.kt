package app.termora.plugins.cos

import app.termora.protocol.ProtocolProvider
import app.termora.protocol.ProtocolProviderExtension

class COSProtocolProviderExtension private constructor() : ProtocolProviderExtension {
    companion object {
        val instance by lazy { COSProtocolProviderExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return COSProtocolProvider.Companion.instance
    }
}