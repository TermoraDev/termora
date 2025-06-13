package app.termora.terminal.panel.vw

import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import app.termora.plugin.internal.ssh.SSHTerminalTab
import org.apache.commons.lang3.StringUtils
import java.util.*

abstract class SSHVisualWindow(
    protected val tab: SSHTerminalTab,
    id: String,
    visualWindowManager: VisualWindowManager
) : VisualWindowPanel(id, visualWindowManager), Resumeable {

    override fun toggleWindow() {
        val evt = AnActionEvent(tab.getJComponent(), StringUtils.EMPTY, EventObject(this))
        val terminalTabbedManager = evt.getData(DataProviders.TerminalTabbedManager) ?: return

        super.toggleWindow()

        if (!isWindow()) {
            terminalTabbedManager.setSelectedTerminalTab(tab)
        }
    }


    override fun getWindowTitle(): String {
        return tab.getTitle() + " - " + title
    }
}