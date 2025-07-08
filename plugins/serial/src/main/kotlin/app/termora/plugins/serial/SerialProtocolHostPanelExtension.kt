package app.termora.plugins.serial

import app.termora.account.AccountOwner
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

    override fun createProtocolHostPanel(accountOwner: AccountOwner): ProtocolHostPanel {
        return SerialProtocolHostPanel()
    }

    override fun ordered(): Long {
        return 5
    }
}