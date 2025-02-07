package app.termora.actions

import app.termora.*

class OpenHostAction : AnAction() {
    companion object {
        /**
         * 打开一个主机
         */
        const val OPEN_HOST = "OpenHostAction"
    }

    override fun actionPerformed(evt: AnActionEvent) {
        if (evt !is OpenHostActionEvent) return
        val terminalTabbedManager = evt.getData(DataProviders.TerminalTabbedManager) ?: return
        val windowScope = evt.getData(DataProviders.WindowScope) ?: return

        val tab = when (evt.host.protocol) {
            Protocol.SSH -> SSHTerminalTab(windowScope, evt.host, terminalTabbedManager)
            Protocol.Serial -> SerialTerminalTab(windowScope, evt.host, terminalTabbedManager)
            else -> LocalTerminalTab(windowScope, evt.host, terminalTabbedManager)
        }

        terminalTabbedManager.addTerminalTab(tab)
        tab.start()
    }
}