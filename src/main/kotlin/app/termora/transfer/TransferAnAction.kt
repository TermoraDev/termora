package app.termora.transfer

import app.termora.HostTerminalTab
import app.termora.I18n
import app.termora.Icons
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import app.termora.protocol.TransferProtocolProvider

class TransferAnAction : AnAction(I18n.getString("termora.transport.sftp"), Icons.folder) {
    override fun actionPerformed(evt: AnActionEvent) {
        val terminalTabbedManager = evt.getData(DataProviders.TerminalTabbedManager) ?: return

        var sftpTab: TransportTerminalTab? = null
        for (tab in terminalTabbedManager.getTerminalTabs()) {
            if (tab is TransportTerminalTab) {
                sftpTab = tab
                break
            }
        }

        // 创建一个新的
        if (sftpTab == null) {
            sftpTab = TransportTerminalTab()
            terminalTabbedManager.addTerminalTab(sftpTab, false)
        }

        var host = if (evt is TransferActionEvent) evt.host else null

        if (host == null) {
            val tab = terminalTabbedManager.getSelectedTerminalTab()
            // 如果当前选中的是 Host 主机
            if (tab is HostTerminalTab) {
                if (TransferProtocolProvider.valueOf(tab.host.protocol) != null) {
                    host = tab.host
                }
            }
        }

        val tabbed = sftpTab.rightTabbed

        // 如果已经打开了 那么直接选中
        if (host != null) {
            for (i in 0 until tabbed.tabCount) {
                val panel = tabbed.getTransportPanel(i) ?: continue
                if (panel.host.id == host.id) {
                    tabbed.selectedIndex = i
                    return
                }
            }
        }

        var selectionPane: TransportSelectionPanel? = null
        for (i in 0 until tabbed.tabCount) {
            val c = tabbed.getComponentAt(i)
            if (c is TransportSelectionPanel) {
                if (c.state == TransportSelectionPanel.State.Initialized) {
                    selectionPane = c
                    break
                }
            }
        }

        if (selectionPane == null) {
            selectionPane = tabbed.addSelectionTab()
        }

        if (host != null) {
            selectionPane.connect(host)
        }

        terminalTabbedManager.setSelectedTerminalTab(sftpTab)
    }
}