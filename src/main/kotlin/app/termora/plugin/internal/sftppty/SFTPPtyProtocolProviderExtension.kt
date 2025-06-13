package app.termora.plugin.internal.sftppty

import app.termora.protocol.ProtocolProvider
import app.termora.protocol.ProtocolProviderExtension

internal class SFTPPtyProtocolProviderExtension private constructor() : ProtocolProviderExtension {
    companion object {
        val instance by lazy { SFTPPtyProtocolProviderExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return SFTPPtyProtocolProvider.instance
    }
}