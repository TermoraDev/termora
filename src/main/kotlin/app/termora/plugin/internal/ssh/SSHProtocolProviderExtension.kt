package app.termora.plugin.internal.ssh

import app.termora.protocol.ProtocolProvider
import app.termora.protocol.ProtocolProviderExtension

internal class SSHProtocolProviderExtension private constructor() : ProtocolProviderExtension {
    companion object {
        val instance by lazy { SSHProtocolProviderExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return SSHProtocolProvider.instance
    }
}