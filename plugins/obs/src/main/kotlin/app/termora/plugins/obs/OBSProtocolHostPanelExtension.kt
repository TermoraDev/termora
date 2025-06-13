package app.termora.plugins.obs

import app.termora.protocol.ProtocolHostPanel
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProvider

class OBSProtocolHostPanelExtension private constructor() : ProtocolHostPanelExtension {
    companion object {
        val instance by lazy { OBSProtocolHostPanelExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return OBSProtocolProvider.instance
    }

    override fun createProtocolHostPanel(): ProtocolHostPanel {
        return OBSProtocolHostPanel()
    }
}