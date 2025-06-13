package app.termora.plugin.internal.ssh

import app.termora.protocol.ProtocolHostPanel
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProvider

internal class SSHProtocolHostPanelExtension private constructor() : ProtocolHostPanelExtension {
    companion object {
        val instance by lazy { SSHProtocolHostPanelExtension() }

    }

    override fun getProtocolProvider(): ProtocolProvider {
        return SSHProtocolProvider.instance
    }

    override fun createProtocolHostPanel(): ProtocolHostPanel {
        return SSHProtocolHostPanel()
    }

}