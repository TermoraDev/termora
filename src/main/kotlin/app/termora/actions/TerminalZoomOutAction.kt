package app.termora.actions

import app.termora.Database
import app.termora.I18n
import kotlin.math.max

class TerminalZoomOutAction : TerminalZoomAction() {
    companion object {
        const val ZOOM_OUT = "TerminalZoomOutAction"
    }

    init {
        putValue(ACTION_COMMAND_KEY, ZOOM_OUT)
        putValue(SHORT_DESCRIPTION, I18n.getString("termora.actions.zoom-out-terminal"))
    }

    override fun zoom(): Boolean {
        val oldFontSize = fontSize
        Database.getDatabase().terminal.fontSize = max(fontSize - 2, 9)
        return oldFontSize != fontSize
    }
}