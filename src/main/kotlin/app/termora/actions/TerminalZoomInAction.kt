package app.termora.actions

import app.termora.Database
import app.termora.I18n

class TerminalZoomInAction : TerminalZoomAction() {
    companion object {
        const val ZOOM_IN = "TerminalZoomInAction"
    }

    init {
        putValue(ACTION_COMMAND_KEY, ZOOM_IN)
        putValue(SHORT_DESCRIPTION, I18n.getString("termora.actions.zoom-in-terminal"))
    }

    override fun zoom(): Boolean {
        Database.getDatabase().terminal.fontSize += 2
        return true
    }
}