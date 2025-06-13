package app.termora.plugin.internal.serial

import app.termora.protocol.ProtocolHostPanel
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProvider

internal class SerialProtocolHostPanelExtension private constructor() : ProtocolHostPanelExtension {
    companion object {
        val instance by lazy { SerialProtocolHostPanelExtension() }

    }

    override fun getProtocolProvider(): ProtocolProvider {
        return SerialProtocolProvider.instance
    }

    override fun createProtocolHostPanel(): ProtocolHostPanel {
        return SerialProtocolHostPanel()
    }

}