package app.termora.actions

import app.termora.terminal.DataKey
import app.termora.tree.NewHostTree

object DataProviders {
    val TerminalPanel = DataKey(app.termora.terminal.panel.TerminalPanel::class)
    val Terminal = DataKey(app.termora.terminal.Terminal::class)
    val TerminalWriter get() = DataKey.TerminalWriter

    val TabbedPane = DataKey(app.termora.MyTabbedPane::class)
    val TerminalTabbed = DataKey(app.termora.TerminalTabbed::class)
    val TerminalTab = DataKey(app.termora.TerminalTab::class)
    val TerminalTabbedManager = DataKey(app.termora.TerminalTabbedManager::class)

    val TermoraFrame = DataKey(app.termora.TermoraFrame::class)
    val WindowScope = DataKey(app.termora.WindowScope::class)


    object Welcome {
        val HostTree = DataKey(NewHostTree::class)
    }
}