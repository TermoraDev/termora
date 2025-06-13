package app.termora.terminal.panel

import app.termora.*
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProvider
import app.termora.actions.DataProviders
import app.termora.database.DatabaseManager
import app.termora.plugin.internal.ssh.SSHTerminalTab
import app.termora.snippet.SnippetAction
import app.termora.snippet.SnippetTreeDialog
import app.termora.terminal.DataKey
import app.termora.terminal.panel.vw.NvidiaSMIVisualWindow
import app.termora.terminal.panel.vw.SystemInformationVisualWindow
import com.formdev.flatlaf.extras.components.FlatToolBar
import com.formdev.flatlaf.ui.FlatRoundBorder
import org.apache.commons.lang3.StringUtils
import java.awt.event.ActionListener
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.*
import javax.swing.JButton
import javax.swing.SwingUtilities

class FloatingToolbarPanel : FlatToolBar(), Disposable {
    private val floatingToolbarEnable get() = DatabaseManager.getInstance().terminal.floatingToolbar
    private var closed = false
    private val anEvent get() = AnActionEvent(this, StringUtils.EMPTY, EventObject(this))

    companion object {

        val FloatingToolbar = DataKey(FloatingToolbarPanel::class)
        val isPined get() = pinAction.isSelected

        private val pinAction by lazy {
            object : AnAction() {
                private val properties get() = DatabaseManager.getInstance().properties
                private val key = "FloatingToolbar.pined"

                init {
                    setStateAction()
                    isSelected = properties.getString(key, StringUtils.EMPTY).toBoolean()
                }

                override fun actionPerformed(evt: AnActionEvent) {
                    isSelected = !isSelected
                    properties.putString(key, isSelected.toString())
                    actionListeners.forEach { it.actionPerformed(evt) }

                    if (isSelected) {
                        TerminalPanelFactory.getInstance().getTerminalPanels().forEach {
                            it.getData(FloatingToolbar)?.triggerShow()
                        }
                    } else {
                        // 触发者的不隐藏
                        val c = evt.getData(FloatingToolbar)
                        TerminalPanelFactory.getInstance().getTerminalPanels().forEach {
                            val e = it.getData(FloatingToolbar)
                            if (c != e) {
                                e?.triggerHide()
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        border = FlatRoundBorder()
        isFocusable = false
        isFloatable = false
        isVisible = false

        if (floatingToolbarEnable) {
            if (pinAction.isSelected) {
                isVisible = true
            }
        }

        initActions()
        initEvents()
    }

    override fun updateUI() {
        super.updateUI()
        border = FlatRoundBorder()
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

        if (isVisible) {
            isVisible = false
            firePropertyChange("visible", true, false)
        }
    }

    private fun initActions() {
        // Pin
        add(initPinActionButton())

        // 服务器信息
        add(initServerInfoActionButton())

        // Snippet
        add(initSnippetActionButton())

        // Nvidia 显卡信息
        add(initNvidiaSMIActionButton())

        // 重连
        add(initReconnectActionButton())

        // 关闭
        add(initCloseActionButton())
    }

    private fun initEvents() {
        // 被添加到组件后
        addPropertyChangeListener("ancestor", object : PropertyChangeListener {
            override fun propertyChange(evt: PropertyChangeEvent) {
                removePropertyChangeListener("ancestor", this)
                SwingUtilities.invokeLater { resumeVisualWindows() }
            }
        })
    }

    @Suppress("UNCHECKED_CAST")
    private fun resumeVisualWindows() {
        val tab = anEvent.getData(DataProviders.TerminalTab) ?: return
        if (tab !is SSHTerminalTab) return
        val terminalPanel = tab.getData(DataProviders.TerminalPanel) ?: return
        terminalPanel.resumeVisualWindows(tab.host.id, object : DataProvider {
            override fun <T : Any> getData(dataKey: DataKey<T>): T? {
                if (dataKey == DataProviders.TerminalTab) {
                    return tab as T
                }
                return super.getData(dataKey)
            }
        })
    }


    private fun initServerInfoActionButton(): JButton {
        val btn = JButton(Icons.infoOutline)
        btn.toolTipText = I18n.getString("termora.visual-window.system-information")
        btn.addActionListener(object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                val tab = anEvent.getData(DataProviders.TerminalTab) ?: return
                val terminalPanel = (tab as DataProvider?)?.getData(DataProviders.TerminalPanel) ?: return

                if (tab !is SSHTerminalTab) {
                    terminalPanel.toast(I18n.getString("termora.floating-toolbar.not-supported"))
                    return
                }

                for (window in terminalPanel.getVisualWindows()) {
                    if (window is SystemInformationVisualWindow) {
                        terminalPanel.moveToFront(window)
                        return
                    }
                }

                val visualWindowPanel = SystemInformationVisualWindow(tab, terminalPanel)
                terminalPanel.addVisualWindow(visualWindowPanel)

            }
        })
        return btn
    }

    private fun initSnippetActionButton(): JButton {
        val btn = JButton(Icons.codeSpan)
        btn.toolTipText = I18n.getString("termora.snippet.title")
        btn.addActionListener(object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                val tab = anEvent.getData(DataProviders.TerminalTab) ?: return
                val writer = tab.getData(DataProviders.TerminalWriter) ?: return
                val dialog = SnippetTreeDialog(evt.window)
                dialog.setLocationRelativeTo(btn)
                dialog.setLocation(dialog.x, btn.locationOnScreen.y + height + 2)
                dialog.isVisible = true
                val node = dialog.getSelectedNode() ?: return
                SnippetAction.getInstance().runSnippet(node.data, writer)
            }
        })
        return btn
    }

    private fun initNvidiaSMIActionButton(): JButton {
        val btn = JButton(Icons.nvidia)
        btn.toolTipText = I18n.getString("termora.visual-window.nvidia-smi")
        btn.addActionListener(object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                val tab = anEvent.getData(DataProviders.TerminalTab) ?: return
                val terminalPanel = (tab as DataProvider?)?.getData(DataProviders.TerminalPanel) ?: return

                if (tab !is SSHTerminalTab) {
                    terminalPanel.toast(I18n.getString("termora.floating-toolbar.not-supported"))
                    return
                }

                for (window in terminalPanel.getVisualWindows()) {
                    if (window is NvidiaSMIVisualWindow) {
                        terminalPanel.moveToFront(window)
                        return
                    }
                }

                val visualWindowPanel = NvidiaSMIVisualWindow(tab, terminalPanel)
                terminalPanel.addVisualWindow(visualWindowPanel)

            }
        })
        return btn
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
        btn.toolTipText = I18n.getString("termora.floating-toolbar.close-in-current-tab")
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
                val tab = anEvent.getData(DataProviders.TerminalTab) ?: return
                if (tab.canReconnect()) {
                    tab.reconnect()
                }
            }
        })
        return btn
    }

}