package app.termora

import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.nv.osx.NativeMacLibrary
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.util.SystemInfo
import com.jetbrains.JBR
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

abstract class DialogWrapper(owner: Window?) : JDialog(owner) {
    private val titleLabel = JLabel()
    val disposable = Disposer.newDisposable()
    private val customTitleBar = if (SystemInfo.isMacOS && JBR.isWindowDecorationsSupported())
        JBR.getWindowDecorations().createCustomTitleBar() else null

    companion object {
        const val DEFAULT_ACTION = "DEFAULT_ACTION"
        private const val PROCESS_GLOBAL_KEYMAP = "PROCESS_GLOBAL_KEYMAP"
    }

    protected var controlsVisible = true
        set(value) {
            field = value
            if (SystemInfo.isMacOS) {
                if (customTitleBar != null) {
                    customTitleBar.putProperty("controls.visible", value)
                } else {
                    NativeMacLibrary.setControlsVisible(this, value)
                }
            } else {
                rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICONIFFY, value)
                rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_MAXIMIZE, value)
                rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_CLOSE, value)
            }
        }

    protected var fullWindowContent = false
        set(value) {
            field = value
            rootPane.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, value)
        }

    protected var titleVisible = true
        set(value) {
            field = value
            rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE, value)
        }

    protected var titleIconVisible = false
        set(value) {
            field = value
            rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICON, value)
        }


    protected var titleBarHeight = UIManager.getInt("TabbedPane.tabHeight")
        set(value) {
            field = value
            if (SystemInfo.isMacOS) {
                customTitleBar?.height = height.toFloat()
            } else {
                rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_HEIGHT, value)
            }
        }

    protected var lostFocusDispose = false
    protected var escapeDispose = true
    var processGlobalKeymap: Boolean
        get() {
            val v = super.rootPane.getClientProperty(PROCESS_GLOBAL_KEYMAP)
            if (v is Boolean) {
                return v
            }
            return false
        }
        protected set(value) {
            super.rootPane.putClientProperty(PROCESS_GLOBAL_KEYMAP, value)
        }

    init {
        super.setDefaultCloseOperation(DISPOSE_ON_CLOSE)

        // 使用 FlatLaf 的 TitlePane
        if (SystemInfo.isWindows || SystemInfo.isLinux) {
            rootPane.windowDecorationStyle = JRootPane.PLAIN_DIALOG
        }
    }

    protected fun init() {
        initEvents()

        val rootPanel = JPanel(BorderLayout())
        rootPanel.add(createCenterPanel(), BorderLayout.CENTER)

        if (SystemInfo.isMacOS) {
            rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            rootPane.putClientProperty(
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_MEDIUM
            )

            val titlePanel = createTitlePanel()
            if (titlePanel != null) {
                rootPanel.add(titlePanel, BorderLayout.NORTH)
            }

            val customTitleBar = this.customTitleBar
            if (customTitleBar != null) {
                customTitleBar.putProperty("controls.visible", controlsVisible)
                customTitleBar.height = titleBarHeight.toFloat()
                JBR.getWindowDecorations().setCustomTitleBar(this, customTitleBar)
            }
        }

        val southPanel = createSouthPanel()
        if (southPanel != null) {
            rootPanel.add(southPanel, BorderLayout.SOUTH)
        }

        rootPane.contentPane = rootPanel
    }

    protected open fun createSouthPanel(): JComponent? {
        val box = Box.createHorizontalBox()
        box.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        )
        box.add(Box.createHorizontalGlue())

        val actions = createActions()
        for (i in actions.size - 1 downTo 0) {
            box.add(createJButtonForAction(actions[i]))
            if (i != 0) {
                box.add(Box.createHorizontalStrut(8))
            }
        }

        return box
    }

    protected open fun createActions(): List<AbstractAction> {
        return listOf(createOkAction(), createCancelAction())
    }

    protected open fun createOkAction(): AbstractAction {
        return OkAction()
    }

    protected open fun createCancelAction(): AbstractAction {
        return CancelAction()
    }

    protected open fun createJButtonForAction(action: Action): JButton {
        val button = JButton(action)
        val value = action.getValue(DEFAULT_ACTION)
        if (value is Boolean && value) {
            rootPane.defaultButton = button
        }
        return button
    }

    protected open fun createTitlePanel(): JPanel? {
        titleLabel.horizontalAlignment = SwingConstants.CENTER
        titleLabel.verticalAlignment = SwingConstants.CENTER
        titleLabel.text = title
        titleLabel.putClientProperty("FlatLaf.style", "font: bold")

        val panel = JPanel(BorderLayout())
        panel.add(titleLabel, BorderLayout.CENTER)
        panel.preferredSize = Dimension(-1, titleBarHeight)


        return panel
    }

    override fun setTitle(title: String?) {
        super.setTitle(title)
        titleLabel.text = title
    }

    protected abstract fun createCenterPanel(): JComponent

    private fun initEvents() {

        val inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        if (escapeDispose) {
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close")
        }

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, toolkit.menuShortcutKeyMaskEx), "close")

        rootPane.actionMap.put("close", object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                val c = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
                if (c != null) {
                    val popups: List<JPopupMenu> = SwingUtils.getDescendantsOfType(
                        JPopupMenu::class.java,
                        c as Container, true
                    )

                    var openPopup = false
                    for (p in popups) {
                        p.isVisible = false
                        openPopup = true
                    }

                    val window = c as? Window ?: SwingUtilities.windowForComponent(c)
                    if (window != null) {
                        val windows = window.ownedWindows
                        for (w in windows) {
                            if (w.isVisible && w.javaClass.getName().endsWith("HeavyWeightWindow")) {
                                openPopup = true
                                w.dispose()
                            }
                        }
                    }

                    if (openPopup) {
                        return
                    }
                }

                SwingUtilities.invokeLater { doCancelAction() }
            }
        })

        addWindowFocusListener(object : WindowAdapter() {
            override fun windowLostFocus(e: WindowEvent) {
                if (lostFocusDispose) {
                    SwingUtilities.invokeLater { doCancelAction() }
                }
            }
        })

        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {
                Disposer.dispose(disposable)
            }
        })

    }

    override fun addNotify() {
        super.addNotify()

        // 显示后触发一次重绘制
        if (SystemInfo.isWindows || SystemInfo.isLinux) {
            this.controlsVisible = controlsVisible
            this.titleBarHeight = titleBarHeight
            this.titleIconVisible = titleIconVisible
            this.titleVisible = titleVisible
            this.fullWindowContent = fullWindowContent
        }

    }

    protected open fun doOKAction() {
        dispose()
    }

    protected open fun doCancelAction() {
        dispose()
    }

    protected inner class OkAction(text: String = I18n.getString("termora.confirm")) : AnAction(text) {
        init {
            putValue(DEFAULT_ACTION, true)
        }


        override fun actionPerformed(evt: AnActionEvent) {
            doOKAction()
        }

    }

    protected inner class CancelAction : AnAction(I18n.getString("termora.cancel")) {

        override fun actionPerformed(evt: AnActionEvent) {
            doCancelAction()
        }

    }
}