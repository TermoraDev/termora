package app.termora.plugins.ftp

import app.termora.account.AccountOwner
import app.termora.protocol.ProtocolHostPanel
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProvider

class FTPProtocolHostPanelExtension private constructor() : ProtocolHostPanelExtension {
    companion object {
        val instance = FTPProtocolHostPanelExtension()
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return FTPProtocolProvider.instance
    }

    override fun createProtocolHostPanel(accountOwner: AccountOwner): ProtocolHostPanel {
        return FTPProtocolHostPanel()
    }
}