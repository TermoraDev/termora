package app.termora

import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.util.SystemInfo
import com.jetbrains.JBR
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Window
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

abstract class DialogWrapper(owner: Window?) : JDialog(owner) {
    private val rootPanel = JPanel(BorderLayout())
    private val titleLabel = JLabel()
    private val titleBar by lazy { LogicCustomTitleBar.createCustomTitleBar(this) }
    val disposable = Disposer.newDisposable()

    companion object {
        const val DEFAULT_ACTION = "DEFAULT_ACTION"
    }


    protected var controlsVisible = true
        set(value) {
            field = value
            titleBar.putProperty("controls.visible", value)
        }

    protected var titleBarHeight = UIManager.getInt("TabbedPane.tabHeight").toFloat()
        set(value) {
            titleBar.height = value
            field = value
        }

    protected var lostFocusDispose = false
    protected var escapeDispose = true

    protected fun init() {

        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE

        initTitleBar()
        initEvents()

        if (JBR.isWindowDecorationsSupported()) {
            if (rootPane.getClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE) != false) {
                val titlePanel = createTitlePanel()
                if (titlePanel != null) {
                    rootPanel.add(titlePanel, BorderLayout.NORTH)
                }
            }
        }

        rootPanel.add(createCenterPanel(), BorderLayout.CENTER)

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
        return listOf(createOkAction(), CancelAction())
    }

    protected open fun createOkAction(): AbstractAction {
        return OkAction()
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
        panel.preferredSize = Dimension(-1, titleBar.height.toInt())


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
            override fun actionPerformed(e: ActionEvent) {
                doCancelAction()
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

        if (SystemInfo.isWindows) {
            addWindowListener(object : WindowAdapter(), ThemeChangeListener {
                override fun windowClosed(e: WindowEvent) {
                    ThemeManager.instance.removeThemeChangeListener(this)
                }

                override fun windowOpened(e: WindowEvent) {
                    onChanged()
                    ThemeManager.instance.addThemeChangeListener(this)
                }

                override fun onChanged() {
                    titleBar.putProperty("controls.dark", FlatLaf.isLafDark())
                }
            })
        }
    }

    private fun initTitleBar() {
        titleBar.height = titleBarHeight
        titleBar.putProperty("controls.visible", controlsVisible)
        if (JBR.isWindowDecorationsSupported()) {
            JBR.getWindowDecorations().setCustomTitleBar(this, titleBar)
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

        override fun actionPerformed(e: ActionEvent) {
            doOKAction()
        }

    }

    protected inner class CancelAction : AnAction(I18n.getString("termora.cancel")) {

        override fun actionPerformed(e: ActionEvent) {
            doCancelAction()
        }

    }
}