package app.termora.actions

import app.termora.I18n
import app.termora.database.DatabaseManager

class TerminalZoomResetAction : TerminalZoomAction() {
    companion object {
        const val ZOOM_RESET = "TerminalZoomResetAction"
    }

    init {
        putValue(ACTION_COMMAND_KEY, ZOOM_RESET)
        putValue(SHORT_DESCRIPTION, I18n.getString("termora.actions.zoom-reset-terminal"))
    }

    private val defaultFontSize = 14

    override fun zoom(): Boolean {
        if (fontSize == defaultFontSize) {
            return false
        }

        DatabaseManager.getInstance().terminal.fontSize = defaultFontSize
        return true
    }
}