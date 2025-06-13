package app.termora.plugin.internal.sftppty

import app.termora.Host
import app.termora.TerminalTab
import app.termora.WindowScope
import app.termora.actions.DataProvider
import app.termora.protocol.GenericProtocolProvider

internal class SFTPPtyProtocolProvider private constructor() : GenericProtocolProvider {
    companion object {
        val instance by lazy { SFTPPtyProtocolProvider() }
        const val PROTOCOL = "SFTPPty"
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun isTransient(): Boolean {
        return true
    }

    override fun createTerminalTab(dataProvider: DataProvider, windowScope: WindowScope, host: Host): TerminalTab {
        return SFTPPtyTerminalTab(windowScope, host)
    }

    override fun canCreateTerminalTab(dataProvider: DataProvider, windowScope: WindowScope, host: Host): Boolean {
        return SFTPPtyTerminalTab.canSupports
    }

}