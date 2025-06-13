package app.termora.plugin.internal.rdp

import app.termora.protocol.ProtocolProvider
import app.termora.protocol.ProtocolProviderExtension

internal class RDPProtocolProviderExtension private constructor() : ProtocolProviderExtension {
    companion object {
        val instance by lazy { RDPProtocolProviderExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return RDPProtocolProvider.instance
    }
}