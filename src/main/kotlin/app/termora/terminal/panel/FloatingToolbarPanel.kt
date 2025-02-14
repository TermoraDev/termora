package app.termora.terminal.panel

import app.termora.*
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import app.termora.terminal.DataKey
import com.formdev.flatlaf.extras.components.FlatToolBar
import com.formdev.flatlaf.ui.FlatRoundBorder
import org.apache.commons.lang3.StringUtils
import java.awt.event.ActionListener
import javax.swing.JButton

class FloatingToolbarPanel : FlatToolBar(), Disposable {
    private val floatingToolbarEnable get() = Database.getDatabase().terminal.floatingToolbar
    private var closed = false

    companion object {

        val FloatingToolbar = DataKey(FloatingToolbarPanel::class)
        val isPined get() = pinAction.isSelected

        private val pinAction by lazy {
            object : AnAction() {
                private val properties get() = Database.getDatabase().properties
                private val key = "FloatingToolbar.pined"

                init {
                    setStateAction()
                    isSelected = properties.getString(key, StringUtils.EMPTY).toBoolean()
                }

                override fun actionPerformed(evt: AnActionEvent) {
                    isSelected = !isSelected
                    properties.putString(key, isSelected.toString())
                    actionListeners.forEach { it.actionPerformed(evt) }
                }
            }
        }
    }

    init {
        border = FlatRoundBorder()
        isOpaque = false
        isFocusable = false
        isFloatable = false
        isVisible = false

        if (floatingToolbarEnable) {
            if (pinAction.isSelected) {
                isVisible = true
            }
        }

        initActions()
    }

    fun triggerShow() {
        if (!floatingToolbarEnable || closed) {
            return
        }

        if (isVisible == false) {
            isVisible = true
            firePropertyChange("visible", false, true)
        }
    }

    fun triggerHide() {
        if (floatingToolbarEnable && !closed) {
            if (pinAction.isSelected) {
                return
            }
        }

        if (isVisible == true) {
            isVisible = false
            firePropertyChange("visible", true, false)
        }
    }

    private fun initActions() {
        // Pin
        add(initPinActionButton())

        // 重连
        add(initReconnectActionButton())

        // 关闭
        add(initCloseActionButton())
    }

    private fun initPinActionButton(): JButton {
        val btn = JButton(Icons.pin)
        btn.isSelected = pinAction.isSelected

        val actionListener = ActionListener { btn.isSelected = pinAction.isSelected }
        pinAction.addActionListener(actionListener)
        btn.addActionListener(pinAction)

        Disposer.register(this, object : Disposable {
            override fun dispose() {
                btn.removeActionListener(pinAction)
                pinAction.removeActionListener(actionListener)
            }
        })

        return btn
    }

    private fun initCloseActionButton(): JButton {
        val btn = JButton(Icons.closeSmall)
        btn.pressedIcon = Icons.closeSmallHovered
        btn.rolloverIcon = Icons.closeSmallHovered
        btn.addActionListener {
            closed = true
            triggerHide()
        }
        return btn
    }

    private fun initReconnectActionButton(): JButton {
        val btn = JButton(Icons.refresh)
        btn.toolTipText = I18n.getString("termora.tabbed.contextmenu.reconnect")

        btn.addActionListener(object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                val tab = evt.getData(DataProviders.TerminalTab) ?: return
                if (tab.canReconnect()) {
                    tab.reconnect()
                }
            }
        })
        return btn
    }

    override fun dispose() {

    }

}