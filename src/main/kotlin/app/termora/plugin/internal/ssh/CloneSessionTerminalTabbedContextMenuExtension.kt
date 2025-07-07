package app.termora.plugin.internal.ssh

import app.termora.I18n
import app.termora.TerminalTab
import app.termora.TerminalTabbedContextMenuExtension
import app.termora.WindowScope
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import javax.swing.JMenuItem

class CloneSessionTerminalTabbedContextMenuExtension private constructor() : TerminalTabbedContextMenuExtension {
    companion object {
        val instance = CloneSessionTerminalTabbedContextMenuExtension()
    }

    override fun createJMenuItem(
        windowScope: WindowScope,
        tab: TerminalTab
    ): JMenuItem {
        if (tab is SSHTerminalTab) {
            if (tab.host.protocol == SSHProtocolProvider.PROTOCOL) {
                val cloneSession = JMenuItem(I18n.getString("termora.tabbed.contextmenu.clone-session"))
                val c = tab.getData(SSHTerminalTab.MySshHandler)
                cloneSession.isEnabled = c?.channel?.isOpen == true
                if (c != null) {
                    cloneSession.addActionListener(object : AnAction() {
                        override fun actionPerformed(evt: AnActionEvent) {
                            val terminalTabbedManager = evt.getData(DataProviders.TerminalTabbedManager) ?: return
                            val handler = c.copy(channel = null)
                            val newTab = SSHTerminalTab(windowScope, tab.host, handler)
                            terminalTabbedManager.addTerminalTab(newTab)
                            newTab.start()
                        }
                    })
                }
                return cloneSession
            }
        }
        throw UnsupportedOperationException()
    }

    override fun ordered(): Long {
        return 0
    }

}