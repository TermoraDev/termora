package app.termora.macro

import app.termora.*

import org.jdesktop.swingx.action.ActionManager
import javax.swing.Icon

class MacroStopRecordingAction(icon: Icon = Icons.stop) : AnAction(
    I18n.getString("termora.macro.stop-recording"),
    icon
) {
    private val macroAction get() = ActionManager.getInstance().getAction(Actions.MACRO) as MacroAction?

    override fun actionPerformed(evt: AnActionEvent) {
        macroAction?.stopRecording()
    }

    override fun isEnabled(): Boolean {
        return macroAction?.isRecording ?: false
    }
}