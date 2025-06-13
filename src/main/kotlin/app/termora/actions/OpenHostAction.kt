package app.termora.actions

import app.termora.*
import app.termora.protocol.GenericProtocolProvider
import app.termora.protocol.ProtocolProvider
import app.termora.sftp.SFTPActionEvent
import org.apache.commons.lang3.StringUtils
import javax.swing.JOptionPane

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
        val host = evt.host

        var tab: TerminalTab? = null
        var providers = ProtocolProvider.providers

        if (providers.none { StringUtils.equalsIgnoreCase(it.getProtocol(), host.protocol) }) {
            OptionPane.showMessageDialog(
                windowScope.window,
                "Protocol ${host.protocol} not supported",
                messageType = JOptionPane.ERROR_MESSAGE,
            )
            return
        }

        // 如果是传输协议
        if (providers.first { StringUtils.equalsIgnoreCase(it.getProtocol(), host.protocol) }
                .isTransfer()) {
            ActionManager.getInstance().getAction(Actions.SFTP)
                .actionPerformed(SFTPActionEvent(evt.source, evt.host.id, evt.event))
            return
        }

        // 只处理通用协议
        providers = providers.filterIsInstance<GenericProtocolProvider>()

        for (provider in providers) {
            if (StringUtils.equalsIgnoreCase(provider.getProtocol(), host.protocol)) {
                if (provider.canCreateTerminalTab(evt, windowScope, host)) {
                    tab = provider.createTerminalTab(evt, windowScope, host)
                    break
                }
            }
        }

        if (tab == null) return

        terminalTabbedManager.addTerminalTab(tab)
        if (tab is PtyHostTerminalTab) {
            tab.start()
        }

    }


}