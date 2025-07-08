package app.termora.plugins.ftp

import app.termora.protocol.ProtocolProvider
import app.termora.protocol.ProtocolProviderExtension

class FTPProtocolProviderExtension private constructor() : ProtocolProviderExtension {
    companion object {
        val instance = FTPProtocolProviderExtension()
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return FTPProtocolProvider.instance
    }
}