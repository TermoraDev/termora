package app.termora.plugin.internal.rdp

import app.termora.protocol.ProtocolHostPanel
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProvider

internal class RDPProtocolHostPanelExtension private constructor() : ProtocolHostPanelExtension {
    companion object {
        val instance by lazy { RDPProtocolHostPanelExtension() }

    }

    override fun getProtocolProvider(): ProtocolProvider {
        return RDPProtocolProvider.instance
    }

    override fun createProtocolHostPanel(): ProtocolHostPanel {
        return RDPProtocolHostPanel()
    }
}