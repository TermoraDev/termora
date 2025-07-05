package app.termora.plugin.internal.telnet

import app.termora.*
import app.termora.account.AccountOwner
import app.termora.keymgr.KeyManager
import app.termora.plugin.internal.BasicProxyOption
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatComboBox
import com.formdev.flatlaf.ui.FlatTextBorder
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.lang3.StringUtils
import java.awt.BorderLayout
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.nio.charset.Charset
import javax.swing.*

@Suppress("CascadeIf")
open class TelnetHostOptionsPane(private val accountOwner: AccountOwner) : OptionsPane() {
    protected val generalOption = GeneralOption()

    // telnet 不支持代理密码
    protected val proxyOption = BasicProxyOption(authenticationTypes = listOf())
    protected val terminalOption = TerminalOption()
    protected val owner: Window get() = SwingUtilities.getWindowAncestor(this)

    init {
        addOption(generalOption)
        addOption(proxyOption)
        addOption(terminalOption)
    }


    open fun getHost(): Host {
        val name = generalOption.nameTextField.text
        val protocol = TelnetProtocolProvider.PROTOCOL
        val host = generalOption.hostTextField.text
        val port = (generalOption.portTextField.value ?: 23) as Int
        var authentication = Authentication.No
        var proxy = Proxy.Companion.No
        val authenticationType = generalOption.authenticationTypeComboBox.selectedItem as AuthenticationType

        if (authenticationType == AuthenticationType.Password) {
            authentication = authentication.copy(
                type = authenticationType,
                password = String(generalOption.passwordTextField.password)
            )
        }

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
            serialComm = serialComm,
        )

        return Host(
            name = name,
            protocol = protocol,
            host = host,
            port = port,
            username = generalOption.usernameTextField.text,
            authentication = authentication,
            proxy = proxy,
            sort = System.currentTimeMillis(),
            remark = generalOption.remarkTextArea.text,
            options = options,
        )
    }

    fun setHost(host: Host) {
        generalOption.portTextField.value = host.port
        generalOption.nameTextField.text = host.name
        generalOption.usernameTextField.text = host.username
        generalOption.hostTextField.text = host.host
        generalOption.remarkTextArea.text = host.remark
        generalOption.authenticationTypeComboBox.selectedItem = host.authentication.type
        if (host.authentication.type == AuthenticationType.Password) {
            generalOption.passwordTextField.text = host.authentication.password
        }
        proxyOption.proxyTypeComboBox.selectedItem = host.proxy.type
        proxyOption.proxyHostTextField.text = host.proxy.host
        proxyOption.proxyPasswordTextField.text = host.proxy.password
        proxyOption.proxyUsernameTextField.text = host.proxy.username
        proxyOption.proxyPortTextField.value = host.proxy.port
        proxyOption.proxyAuthenticationTypeComboBox.selectedItem = host.proxy.authenticationType

        terminalOption.charsetComboBox.selectedItem = host.options.encoding
        terminalOption.environmentTextArea.text = host.options.env
        terminalOption.startupCommandTextField.text = host.options.startupCommand

    }

    fun validateFields(): Boolean {
        val host = getHost()

        // general
        if (validateField(generalOption.nameTextField)
            || validateField(generalOption.hostTextField)
        ) {
            return false
        }

        if (StringUtils.equalsIgnoreCase(host.protocol, TelnetProtocolProvider.PROTOCOL)) {
            if (validateField(generalOption.usernameTextField)) {
                return false
            }
        }

        if (host.authentication.type == AuthenticationType.Password) {
            if (validateField(generalOption.passwordTextField)) {
                return false
            }
        } else if (host.authentication.type == AuthenticationType.PublicKey) {
            if (validateField(generalOption.publicKeyComboBox)) {
                return false
            }
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

    /**
     * 返回 true 表示有错误
     */
    private fun validateField(comboBox: JComboBox<*>): Boolean {
        val selectedItem = comboBox.selectedItem
        if (comboBox.isEnabled && (selectedItem == null || (selectedItem is String && selectedItem.isBlank()))) {
            selectOptionJComponent(comboBox)
            comboBox.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR)
            comboBox.requestFocusInWindow()
            return true
        }
        return false
    }

    protected inner class GeneralOption : JPanel(BorderLayout()), Option {
        val portTextField = PortSpinner(23)
        val nameTextField = OutlineTextField(128)
        val usernameTextField = OutlineTextField(128)
        val hostTextField = OutlineTextField(255)
        val passwordTextField = OutlinePasswordField(255)
        val publicKeyComboBox = OutlineComboBox<String>()
        val remarkTextArea = FixedLengthTextArea(512)
        val authenticationTypeComboBox = FlatComboBox<AuthenticationType>()

        init {
            initView()
            initEvents()
        }

        private fun initView() {
            add(getCenterComponent(), BorderLayout.CENTER)

            publicKeyComboBox.isEditable = false

            publicKeyComboBox.renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    var text = StringUtils.EMPTY
                    if (value is String) {
                        text = KeyManager.getInstance().getOhKeyPair(value)?.name ?: text
                    }
                    return super.getListCellRendererComponent(
                        list,
                        text,
                        index,
                        isSelected,
                        cellHasFocus
                    )
                }
            }

            authenticationTypeComboBox.renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    var text = value?.toString() ?: ""
                    when (value) {
                        AuthenticationType.Password -> {
                            text = "Password"
                        }

                        AuthenticationType.PublicKey -> {
                            text = "Public Key"
                        }

                        AuthenticationType.KeyboardInteractive -> {
                            text = "Keyboard Interactive"
                        }
                    }
                    return super.getListCellRendererComponent(
                        list,
                        text,
                        index,
                        isSelected,
                        cellHasFocus
                    )
                }
            }

            authenticationTypeComboBox.addItem(AuthenticationType.No)
            authenticationTypeComboBox.addItem(AuthenticationType.Password)

            authenticationTypeComboBox.selectedItem = AuthenticationType.Password

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
                "pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref"
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

                .add("${I18n.getString("termora.new-host.general.username")}:").xy(1, rows)
                .add(usernameTextField).xyw(3, rows, 5).apply { rows += step }

                .add("${I18n.getString("termora.new-host.general.authentication")}:").xy(1, rows)
                .add(authenticationTypeComboBox).xyw(3, rows, 5).apply { rows += step }

                .add("${I18n.getString("termora.new-host.general.password")}:").xy(1, rows)
                .add(passwordTextField).xyw(3, rows, 5).apply { rows += step }

                .add("${I18n.getString("termora.new-host.general.remark")}:").xy(1, rows)
                .add(JScrollPane(remarkTextArea).apply { border = FlatTextBorder() })
                .xyw(3, rows, 5).apply { rows += step }

                .build()


            return panel
        }

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