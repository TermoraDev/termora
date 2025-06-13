package app.termora.plugin.internal.local

import app.termora.*
import app.termora.plugin.internal.BasicGeneralOption
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.ui.FlatTextBorder
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import java.awt.BorderLayout
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.nio.charset.Charset
import javax.swing.*

internal open class LocalHostOptionsPane : OptionsPane() {
    protected val generalOption = BasicGeneralOption()
    protected val terminalOption = TerminalOption()
    protected val owner: Window get() = SwingUtilities.getWindowAncestor(this)

    init {
        addOption(generalOption)
        addOption(terminalOption)

    }


    open fun getHost(): Host {
        val name = generalOption.nameTextField.text
        val protocol = LocalProtocolProvider.PROTOCOL

        val serialComm = SerialComm()

        val options = Options.Companion.Default.copy(
            encoding = terminalOption.charsetComboBox.selectedItem as String,
            env = terminalOption.environmentTextArea.text,
            startupCommand = terminalOption.startupCommandTextField.text,
            serialComm = serialComm,
        )

        return Host(
            name = name,
            protocol = protocol,
            sort = System.currentTimeMillis(),
            remark = generalOption.remarkTextArea.text,
            options = options,
        )
    }

    fun setHost(host: Host) {
        generalOption.nameTextField.text = host.name
        generalOption.remarkTextArea.text = host.remark

        terminalOption.charsetComboBox.selectedItem = host.options.encoding
        terminalOption.environmentTextArea.text = host.options.env
        terminalOption.startupCommandTextField.text = host.options.startupCommand

    }

    fun validateFields(): Boolean {
        return validateField(generalOption.nameTextField).not()
    }

    /**
     * 返回 true 表示有错误
     */
    private fun validateField(textField: JTextField): Boolean {
        if (textField.isEnabled && textField.text.isBlank()) {
            setOutlineError(textField)
            return true
        }
        return false
    }

    private fun setOutlineError(textField: JTextField) {
        selectOptionJComponent(textField)
        textField.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR)
        textField.requestFocusInWindow()
    }

    protected inner class TerminalOption : JPanel(BorderLayout()), Option {
        val charsetComboBox = JComboBox<String>()
        val startupCommandTextField = OutlineTextField()
        val environmentTextArea = FixedLengthTextArea(2048)


        init {
            initView()
            initEvents()
        }

        private fun initView() {
            add(getCenterComponent(), BorderLayout.CENTER)


            environmentTextArea.setFocusTraversalKeys(
                KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .getDefaultFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)
            )
            environmentTextArea.setFocusTraversalKeys(
                KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
                KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .getDefaultFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS)
            )

            environmentTextArea.rows = 8
            environmentTextArea.lineWrap = true
            environmentTextArea.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)

            for (e in Charset.availableCharsets()) {
                charsetComboBox.addItem(e.key)
            }

            charsetComboBox.selectedItem = "UTF-8"

        }

        private fun initEvents() {

        }


        override fun getIcon(isSelected: Boolean): Icon {
            return Icons.terminal
        }

        override fun getTitle(): String {
            return I18n.getString("termora.new-host.terminal")
        }

        override fun getJComponent(): JComponent {
            return this
        }

        private fun getCenterComponent(): JComponent {
            val layout = FormLayout(
                "left:pref, $FORM_MARGIN, default:grow",
                "pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref"
            )

            var rows = 1
            val step = 2
            val panel = FormBuilder.create().layout(layout)
                .add("${I18n.getString("termora.new-host.terminal.encoding")}:").xy(1, rows)
                .add(charsetComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.new-host.terminal.startup-commands")}:").xy(1, rows)
                .add(startupCommandTextField).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.new-host.terminal.env")}:").xy(1, rows)
                .add(JScrollPane(environmentTextArea).apply { border = FlatTextBorder() }).xy(3, rows)
                .apply { rows += step }
                .build()


            return panel
        }
    }


}