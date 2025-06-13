package app.termora.plugins.ftp

import app.termora.protocol.ProtocolHostPanel
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProvider

class FTPProtocolHostPanelExtension private constructor() : ProtocolHostPanelExtension {
    companion object {
        val instance by lazy { FTPProtocolHostPanelExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return FTPProtocolProvider.instance
    }

    override fun createProtocolHostPanel(): ProtocolHostPanel {
        return FTPProtocolHostPanel()
    }
}