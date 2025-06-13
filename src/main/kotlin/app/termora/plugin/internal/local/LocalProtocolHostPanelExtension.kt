package app.termora.plugin.internal.local

import app.termora.protocol.ProtocolHostPanel
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProvider

internal class LocalProtocolHostPanelExtension private constructor() : ProtocolHostPanelExtension {
    companion object {
        val instance by lazy { LocalProtocolHostPanelExtension() }

    }

    override fun getProtocolProvider(): ProtocolProvider {
        return LocalProtocolProvider.instance
    }

    override fun createProtocolHostPanel(): ProtocolHostPanel {
        return LocalProtocolHostPanel()
    }
}