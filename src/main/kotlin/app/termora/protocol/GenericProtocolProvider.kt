package app.termora.protocol

import app.termora.Host
import app.termora.TerminalTab
import app.termora.WindowScope
import app.termora.actions.DataProvider

interface GenericProtocolProvider : ProtocolProvider {

    /**
     * 创建终端标签
     */
    fun createTerminalTab(
        dataProvider: DataProvider,
        windowScope: WindowScope,
        host: Host
    ): TerminalTab

    /**
     * 是否可以创建 Terminal tab
     */
    fun canCreateTerminalTab(
        dataProvider: DataProvider,
        windowScope: WindowScope,
        host: Host
    ): Boolean = true
}