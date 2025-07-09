package app.termora.actions

import app.termora.ApplicationScope
import app.termora.I18n
import app.termora.Icons
import app.termora.SettingsDialog
import com.formdev.flatlaf.extras.FlatDesktop
import org.apache.commons.lang3.StringUtils
import java.awt.KeyboardFocusManager
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

class SettingsAction private constructor() : AnAction(
    I18n.getString("termora.setting"),
    Icons.settings
) {

    companion object {

        /**
         * 打开设置
         */
        const val SETTING = "SettingAction"

        fun getInstance(): SettingsAction {
            return ApplicationScope.forApplicationScope().getOrCreate(SettingsAction::class) { SettingsAction() }
        }
    }

    private var isShowing = false
    private val action get() = this

    init {
        FlatDesktop.setPreferencesHandler {
            val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            // Doorman 的情况下不允许打开
            if (owner != null && ApplicationScope.windowScopes().isNotEmpty()) {
                actionPerformed(ActionEvent(owner, ActionEvent.ACTION_PERFORMED, StringUtils.EMPTY))
            }
        }
    }

    override fun actionPerformed(evt: AnActionEvent) {
        if (isShowing) return
        showSettingsDialog(evt)
    }


    private fun showSettingsDialog(evt: AnActionEvent) {

        isShowing = true

        val owner = evt.window
        val dialog = SettingsDialog(owner)
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {
                action.isShowing = false
            }
        })
        dialog.setLocationRelativeTo(owner)
        dialog.isVisible = true
    }


}