package app.termora.actions

import app.termora.ApplicationScope
import app.termora.EnableManager
import app.termora.I18n
import app.termora.Icons
import org.slf4j.LoggerFactory

class TerminalFocusModeAction private constructor() : AnAction(
    I18n.getString("termora.actions.focus-mode"),
    Icons.eye
) {

    companion object {
        const val FocusMode = "TerminalFocusMode"
        private val log = LoggerFactory.getLogger(TerminalFocusModeAction::class.java)
        fun getInstance(): TerminalFocusModeAction {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(TerminalFocusModeAction::class) { TerminalFocusModeAction() }
        }
    }

    init {
        putValue(SHORT_DESCRIPTION, I18n.getString("termora.actions.focus-mode"))
        putValue(ACTION_COMMAND_KEY, FocusMode)
        setStateAction()
        isSelected = enableManager.getFlag("Terminal.FocusMode", false)
    }

    private val enableManager get() = EnableManager.getInstance()


    override fun actionPerformed(evt: AnActionEvent) {
        enableManager.setFlag("Terminal.FocusMode", isSelected)
    }

}