package app.termora.transfer.internal.wsl

import app.termora.protocol.ProtocolProvider
import app.termora.protocol.ProtocolProviderExtension

internal class WSLProtocolProviderExtension private constructor() : ProtocolProviderExtension {
    companion object {
        val instance by lazy { WSLProtocolProviderExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return WSLTransferProtocolProvider.instance
    }
}
