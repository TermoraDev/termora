package app.termora.plugins.serial

import app.termora.protocol.ProtocolProvider
import app.termora.protocol.ProtocolProviderExtension

internal class SerialProtocolProviderExtension private constructor() : ProtocolProviderExtension {
    companion object {
        val instance by lazy { SerialProtocolProviderExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return SerialProtocolProvider.instance
    }
}