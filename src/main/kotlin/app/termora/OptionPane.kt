package app.termora

import app.termora.nv.osx.NativeMacLibrary
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatTextPane
import com.formdev.flatlaf.util.SystemInfo
import com.jetbrains.JBR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import org.apache.commons.lang3.StringUtils
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Desktop
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object OptionPane {
    private val coroutineScope = swingCoroutineScope

    fun showConfirmDialog(
        parentComponent: Component?,
        message: Any,
        title: String = UIManager.getString("OptionPane.messageDialogTitle"),
        optionType: Int = JOptionPane.YES_NO_OPTION,
        messageType: Int = JOptionPane.QUESTION_MESSAGE,
        icon: Icon? = null,
        options: Array<Any>? = null,
        initialValue: Any? = null,
        customizeDialog: (JDialog) -> Unit = {},
    ): Int {

        val panel = if (message is JComponent) {
            message
        } else {
            val label = FlatTextPane()
            label.contentType = "text/html"
            label.text = "<html>$message</html>"
            label.isEditable = false
            label.background = null
            label.border = BorderFactory.createEmptyBorder()
            label
        }

        val pane = object : JOptionPane(panel, messageType, optionType, icon, options, initialValue) {
            override fun selectInitialValue() {
                super.selectInitialValue()
                if (message is JComponent) {
                    if (message.getClientProperty("SKIP_requestFocusInWindow") == true) {
                        return
                    }
                    message.requestFocusInWindow()
                }
            }
        }
        val dialog = initDialog(pane.createDialog(parentComponent, title))
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowOpened(e: WindowEvent) {
                pane.selectInitialValue()
            }
        })
        dialog.setLocationRelativeTo(parentComponent)
        customizeDialog.invoke(dialog)
        dialog.isVisible = true
        dialog.dispose()
        val selectedValue = pane.value


        if (selectedValue == null) {
            return -1
        } else if (pane.options == null) {
            return if (selectedValue is Int) selectedValue else -1
        } else {
            var counter = 0

            val maxCounter: Int = pane.options.size
            while (counter < maxCounter) {
                if (pane.options[counter] == selectedValue) {
                    return counter
                }
                ++counter
            }

            return -1
        }
    }

    fun showMessageDialog(
        parentComponent: Component?,
        message: String,
        title: String = UIManager.getString("OptionPane.messageDialogTitle"),
        messageType: Int = JOptionPane.INFORMATION_MESSAGE,
        duration: Duration = 0.milliseconds,
    ) {
        val label = JTextPane()
        label.contentType = "text/html"
        label.text = "<html>$message</html>"
        label.isEditable = false
        label.background = null
        label.border = BorderFactory.createEmptyBorder()
        val pane = JOptionPane(label, messageType, JOptionPane.DEFAULT_OPTION)
        val dialog = initDialog(pane.createDialog(parentComponent, title))
        if (duration.inWholeMilliseconds > 0) {
            dialog.addWindowListener(object : WindowAdapter() {
                override fun windowOpened(e: WindowEvent) {
                    coroutineScope.launch(Dispatchers.Swing) {
                        delay(duration.inWholeMilliseconds)
                        if (dialog.isVisible) {
                            dialog.isVisible = false
                        }
                    }
                }
            })
        }
        pane.selectInitialValue()
        dialog.isVisible = true
        dialog.dispose()
    }

    fun showInputDialog(
        parentComponent: Component?,
        title: String = UIManager.getString("OptionPane.messageDialogTitle"),
        value: String = StringUtils.EMPTY,
        placeholder: String = StringUtils.EMPTY,
    ): String? {
        val pane = JOptionPane(StringUtils.EMPTY, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION)
        val dialog = initDialog(pane.createDialog(parentComponent, title))
        pane.wantsInput = true
        pane.initialSelectionValue = value

        val textField = SwingUtils.getDescendantsOfType(JTextField::class.java, pane, true).firstOrNull()
        if (textField?.name == "OptionPane.textField") {
            textField.border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DynamicColor.BorderColor),
                BorderFactory.createEmptyBorder(0, 0, 2, 0)
            )
            textField.background = UIManager.getColor("window")
            textField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder)
        }

        dialog.isVisible = true
        dialog.dispose()

        val inputValue = pane.inputValue
        if (inputValue == JOptionPane.UNINITIALIZED_VALUE) return null

        return inputValue as? String
    }

    fun openFileInFolder(
        parentComponent: Component,
        file: File,
        yMessage: String,
        nMessage: String? = null,
    ) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop()
                .isSupported(Desktop.Action.BROWSE_FILE_DIR)
        ) {
            if (yMessage.isEmpty() || JOptionPane.YES_OPTION == showConfirmDialog(
                    parentComponent,
                    yMessage,
                    optionType = JOptionPane.YES_NO_OPTION
                )
            ) {
                Desktop.getDesktop().browseFileDirectory(file)
            }
        } else if (nMessage != null) {
            showMessageDialog(
                parentComponent,
                nMessage,
                messageType = JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    private fun initDialog(dialog: JDialog): JDialog {
        if (SystemInfo.isWindows || SystemInfo.isLinux) {
            dialog.rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_CLOSE, false)
            dialog.rootPane.putClientProperty(
                FlatClientProperties.TITLE_BAR_HEIGHT,
                UIManager.getInt("TabbedPane.tabHeight")
            )
        } else if (SystemInfo.isMacOS) {
            dialog.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            dialog.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            dialog.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            dialog.rootPane.putClientProperty(
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_MEDIUM
            )


            val height = UIManager.getInt("TabbedPane.tabHeight") - 10
            if (JBR.isWindowDecorationsSupported()) {
                val customTitleBar = JBR.getWindowDecorations().createCustomTitleBar()
                customTitleBar.putProperty("controls.visible", false)
                customTitleBar.height = height.toFloat()
                JBR.getWindowDecorations().setCustomTitleBar(dialog, customTitleBar)
            } else {
                NativeMacLibrary.setControlsVisible(dialog, false)
            }

            val label = JLabel(dialog.title)
            label.putClientProperty(FlatClientProperties.STYLE, "font: bold")
            val box = Box.createHorizontalBox()
            box.add(Box.createHorizontalGlue())
            box.add(label)
            box.add(Box.createHorizontalGlue())
            box.preferredSize = Dimension(-1, height)
            dialog.contentPane.add(box, BorderLayout.NORTH)
        }
        return dialog
    }
}