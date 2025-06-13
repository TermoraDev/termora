package app.termora.plugins.oss

import app.termora.protocol.ProtocolHostPanel
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProvider

class OSSProtocolHostPanelExtension private constructor() : ProtocolHostPanelExtension {
    companion object {
        val instance by lazy { OSSProtocolHostPanelExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return OSSProtocolProvider.instance
    }

    override fun createProtocolHostPanel(): ProtocolHostPanel {
        return OSSProtocolHostPanel()
    }
}