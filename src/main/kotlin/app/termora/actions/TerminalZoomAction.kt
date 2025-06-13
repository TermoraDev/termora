package app.termora.actions

import app.termora.TerminalPanelFactory
import app.termora.database.DatabaseManager

abstract class TerminalZoomAction : AnAction() {
    protected val fontSize get() = DatabaseManager.getInstance().terminal.fontSize

    abstract fun zoom(): Boolean

    override fun actionPerformed(evt: AnActionEvent) {
        evt.getData(DataProviders.TerminalPanel) ?: return

        if (zoom()) {
                TerminalPanelFactory.getInstance()
                    .fireResize()
            evt.consume()
        }
    }
}