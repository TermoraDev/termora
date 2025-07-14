package app.termora.plugin.internal.local

import app.termora.Host
import app.termora.Options
import app.termora.OptionsPane
import app.termora.SerialComm
import app.termora.plugin.internal.AltKeyModifier
import app.termora.plugin.internal.BasicGeneralOption
import app.termora.plugin.internal.BasicTerminalOption
import com.formdev.flatlaf.FlatClientProperties
import java.awt.Window
import javax.swing.JTextField
import javax.swing.SwingUtilities

internal open class LocalHostOptionsPane : OptionsPane() {
    protected val generalOption = BasicGeneralOption()
    private val terminalOption = BasicTerminalOption().apply {
        showCharsetComboBox = true
        showEnvironmentTextArea = true
        showStartupCommandTextField = true
        init()
    }
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
            extras = mutableMapOf(
                "altModifier" to (terminalOption.altModifierComboBox.selectedItem?.toString()
                    ?: AltKeyModifier.EightBit.name),
            )
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

}