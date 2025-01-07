package app.termora.findeverywhere

import app.termora.*
import com.formdev.flatlaf.FlatLaf
import org.jdesktop.swingx.action.ActionManager
import java.awt.event.ActionEvent
import javax.swing.Icon

class QuickCommandFindEverywhereProvider : FindEverywhereProvider {


    override fun find(pattern: String): List<FindEverywhereResult> {
        val list = mutableListOf<FindEverywhereResult>()
        ActionManager.getInstance().getAction(Actions.ADD_HOST)?.let {
            list.add(CreateHostFindEverywhereResult())
        }

        // Local terminal
        list.add(ActionFindEverywhereResult(object : AnAction(
            I18n.getString("termora.find-everywhere.quick-command.local-terminal"),
            Icons.terminal
        ) {
            override fun actionPerformed(evt: ActionEvent) {
                ActionManager.getInstance().getAction(Actions.OPEN_HOST)
                    ?.actionPerformed(
                        OpenHostActionEvent(
                            this, Host(
                                name = name,
                                protocol = Protocol.Local
                            )
                        )
                    )
            }
        }))

        // SFTP
        list.add(ActionFindEverywhereResult(object : AnAction("SFTP", Icons.fileTransfer) {
            override fun actionPerformed(evt: ActionEvent) {
                val terminalTabbedManager = Application.getService(TerminalTabbedManager::class)
                val tabs = terminalTabbedManager.getTerminalTabs()
                for (i in tabs.indices) {
                    val tab = tabs[i]
                    if (tab is SFTPTerminalTab) {
                        terminalTabbedManager.setSelectedTerminalTab(tab)
                        return
                    }
                }
                // 创建一个新的
                terminalTabbedManager.addTerminalTab(SFTPTerminalTab())
            }
        }))

        return list
    }


    override fun order(): Int {
        return Int.MIN_VALUE
    }

    override fun group(): String {
        return I18n.getString("termora.find-everywhere.groups.quick-actions")
    }

    private class CreateHostFindEverywhereResult : ActionFindEverywhereResult(
        ActionManager.getInstance().getAction(Actions.ADD_HOST)
    ) {
        override fun getIcon(isSelected: Boolean): Icon {
            if (isSelected) {
                if (!FlatLaf.isLafDark()) {
                    return Icons.openNewTab.dark
                }
            }
            return Icons.openNewTab
        }


        override fun toString(): String {
            return I18n.getString("termora.new-host.title")
        }
    }


}