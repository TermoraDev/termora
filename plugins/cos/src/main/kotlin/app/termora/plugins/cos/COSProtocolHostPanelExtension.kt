package app.termora.plugins.cos

import app.termora.protocol.ProtocolHostPanel
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProvider

class COSProtocolHostPanelExtension private constructor() : ProtocolHostPanelExtension {
    companion object {
        val instance by lazy { COSProtocolHostPanelExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return COSProtocolProvider.Companion.instance
    }

    override fun createProtocolHostPanel(): ProtocolHostPanel {
        return COSProtocolHostPanel()
    }
}