package app.termora.plugin.internal.telnet

import app.termora.*
import app.termora.account.AccountOwner
import app.termora.highlight.KeywordHighlight
import app.termora.plugin.internal.AltKeyModifier
import app.termora.plugin.internal.BasicProxyOption
import app.termora.plugin.internal.BasicTerminalOption
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.ui.FlatTextBorder
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import java.awt.BorderLayout
import java.awt.KeyboardFocusManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*

class TelnetHostOptionsPane(private val accountOwner: AccountOwner) : OptionsPane() {
    private val generalOption = GeneralOption()

    // telnet 不支持代理密码
    private val proxyOption = BasicProxyOption(authenticationTypes = listOf())
    private val terminalOption = BasicTerminalOption().apply {
        showCharsetComboBox = true
        showBackspaceComboBox = true
        showStartupCommandTextField = true
        showCharacterAtATimeTextField = true
        showEnvironmentTextArea = true
        showLoginScripts = true
        accountOwner = this@TelnetHostOptionsPane.accountOwner
        init()
    }


    init {
        addOption(generalOption)
        addOption(proxyOption)
        addOption(terminalOption)
    }


    fun getHost(): Host {
        val name = generalOption.nameTextField.text
        val protocol = TelnetProtocolProvider.PROTOCOL
        val host = generalOption.hostTextField.text
        val port = (generalOption.portTextField.value ?: 23) as Int
        var proxy = Proxy.Companion.No

        if (proxyOption.proxyTypeComboBox.selectedItem != ProxyType.No) {
            proxy = proxy.copy(
                type = proxyOption.proxyTypeComboBox.selectedItem as ProxyType,
                host = proxyOption.proxyHostTextField.text,
                username = proxyOption.proxyUsernameTextField.text,
                password = String(proxyOption.proxyPasswordTextField.password),
                port = proxyOption.proxyPortTextField.value as Int,
                authenticationType = proxyOption.proxyAuthenticationTypeComboBox.selectedItem as AuthenticationType,
            )
        }


        val serialComm = SerialComm()

        val options = Options.Companion.Default.copy(
            encoding = terminalOption.charsetComboBox.selectedItem as String,
            env = terminalOption.environmentTextArea.text,
            startupCommand = terminalOption.startupCommandTextField.text,
            loginScripts = terminalOption.loginScripts,
            serialComm = serialComm,
            extras = mutableMapOf(
                "backspace" to (terminalOption.backspaceComboBox.selectedItem as Backspace).name,
                "character-at-a-time" to (terminalOption.characterAtATimeTextField.selectedItem?.toString() ?: "false"),
                "altModifier" to (terminalOption.altModifierComboBox.selectedItem?.toString()
                    ?: AltKeyModifier.EightBit.name),
                "keywordHighlightSetId" to ((terminalOption.highlightSetComboBox.selectedItem as? KeywordHighlight)?.id
                    ?: "-1"),
            )
        )

        return Host(
            name = name,
            protocol = protocol,
            host = host,
            port = port,
            proxy = proxy,
            sort = System.currentTimeMillis(),
            remark = generalOption.remarkTextArea.text,
            options = options,
        )
    }

    fun setHost(host: Host) {
        generalOption.portTextField.value = host.port
        generalOption.nameTextField.text = host.name
        generalOption.hostTextField.text = host.host
        generalOption.remarkTextArea.text = host.remark

        proxyOption.proxyTypeComboBox.selectedItem = host.proxy.type
        proxyOption.proxyHostTextField.text = host.proxy.host
        proxyOption.proxyPasswordTextField.text = host.proxy.password
        proxyOption.proxyUsernameTextField.text = host.proxy.username
        proxyOption.proxyPortTextField.value = host.proxy.port
        proxyOption.proxyAuthenticationTypeComboBox.selectedItem = host.proxy.authenticationType

        terminalOption.charsetComboBox.selectedItem = host.options.encoding
        terminalOption.environmentTextArea.text = host.options.env
        terminalOption.startupCommandTextField.text = host.options.startupCommand
        terminalOption.backspaceComboBox.selectedItem =
            Backspace.valueOf(host.options.extras["backspace"] ?: Backspace.Delete.name)
        terminalOption.characterAtATimeTextField.selectedItem =
            host.options.extras["character-at-a-time"]?.toBooleanStrictOrNull() ?: false
        val altModifier = host.options.extras["altModifier"] ?: AltKeyModifier.EightBit.name
        terminalOption.altModifierComboBox.selectedItem = runCatching { AltKeyModifier.valueOf(altModifier) }
            .getOrNull() ?: AltKeyModifier.EightBit


        val keywordHighlightSetId = host.options.extras["keywordHighlightSetId"]
        for (i in 0 until terminalOption.highlightSetComboBox.itemCount) {
            val item = terminalOption.highlightSetComboBox.getItemAt(i)
            if (item.id == keywordHighlightSetId) {
                terminalOption.highlightSetComboBox.selectedItem = item
                break
            }
        }

        terminalOption.loginScripts.clear()
        terminalOption.loginScripts.addAll(host.options.loginScripts)
    }

    fun validateFields(): Boolean {
        val host = getHost()

        // general
        if (validateField(generalOption.nameTextField)
            || validateField(generalOption.hostTextField)
        ) {
            return false
        }

        // proxy
        if (host.proxy.type != ProxyType.No) {
            if (validateField(proxyOption.proxyHostTextField)
            ) {
                return false
            }

            if (host.proxy.authenticationType != AuthenticationType.No) {
                if (validateField(proxyOption.proxyUsernameTextField)
                    || validateField(proxyOption.proxyPasswordTextField)
                ) {
                    return false
                }
            }
        }

        return true
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

    inner class GeneralOption : JPanel(BorderLayout()), Option {
        val portTextField = PortSpinner(23)
        val nameTextField = OutlineTextField(128)
        val hostTextField = OutlineTextField(255)
        val remarkTextArea = FixedLengthTextArea(512)

        init {
            initView()
            initEvents()
        }

        private fun initView() {
            add(getCenterComponent(), BorderLayout.CENTER)
        }

        private fun initEvents() {
            addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    SwingUtilities.invokeLater { nameTextField.requestFocusInWindow() }
                    removeComponentListener(this)
                }
            })
        }

        override fun getIcon(isSelected: Boolean): Icon {
            return Icons.settings
        }

        override fun getTitle(): String {
            return I18n.getString("termora.new-host.general")
        }

        override fun getJComponent(): JComponent {
            return this
        }

        private fun getCenterComponent(): JComponent {
            val layout = FormLayout(
                "left:pref, $FORM_MARGIN, default:grow, $FORM_MARGIN, pref, $FORM_MARGIN, default",
                "pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref"
            )
            remarkTextArea.setFocusTraversalKeys(
                KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .getDefaultFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)
            )
            remarkTextArea.setFocusTraversalKeys(
                KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
                KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .getDefaultFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS)
            )

            remarkTextArea.rows = 8
            remarkTextArea.lineWrap = true
            remarkTextArea.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)


            var rows = 1
            val step = 2
            val panel = FormBuilder.create().layout(layout)
                .add("${I18n.getString("termora.new-host.general.name")}:").xy(1, rows)
                .add(nameTextField).xyw(3, rows, 5).apply { rows += step }

                .add("${I18n.getString("termora.new-host.general.host")}:").xy(1, rows)
                .add(hostTextField).xy(3, rows)
                .add("${I18n.getString("termora.new-host.general.port")}:").xy(5, rows)
                .add(portTextField).xy(7, rows).apply { rows += step }

                .add("${I18n.getString("termora.new-host.general.remark")}:").xy(1, rows)
                .add(JScrollPane(remarkTextArea).apply { border = FlatTextBorder() })
                .xyw(3, rows, 5).apply { rows += step }

                .build()


            return panel
        }

    }


    enum class Backspace {
        /**
         * 0x08
         */
        Backspace,

        /**
         * 0x7F 默认
         */
        Delete,

        /**
         * ESC[3~
         */
        VT220, ;

        override fun toString(): String {
            return when (this) {
                Backspace -> "ASCII Backspace (0x08)"
                Delete -> "ASCII Delete (0x7F)"
                VT220 -> "VT220 Delete (ESC[3~)"
            }
        }
    }
}