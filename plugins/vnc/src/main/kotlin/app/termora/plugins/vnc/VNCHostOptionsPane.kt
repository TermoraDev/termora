package app.termora.plugins.vnc

import app.termora.*
import app.termora.plugin.internal.BasicProxyOption
import app.termora.plugin.internal.BasicTerminalOption
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
import java.awt.event.ItemEvent
import javax.swing.*

internal open class VNCHostOptionsPane : OptionsPane() {
    protected val generalOption = GeneralOption()
    protected val proxyOption = BasicProxyOption(authenticationTypes = emptyList())
    private val terminalOption = object : BasicTerminalOption() {
        override fun getTitle(): String {
            return "VNC"
        }

        override fun getIcon(isSelected: Boolean): Icon {
            return VNCProtocolProvider.instance.getIcon()
        }
    }.apply {
        showAltModifierComboBox = false
        showCharsetComboBox = true
        init()

        charsetComboBox.selectedItem = "ISO-8859-1"

    }

    protected val owner: Window get() = SwingUtilities.getWindowAncestor(this)

    init {
        addOption(generalOption)
        addOption(proxyOption)
        addOption(terminalOption)
    }


    open fun getHost(): Host {
        val name = generalOption.nameTextField.text
        val protocol = VNCProtocolProvider.PROTOCOL
        val host = generalOption.hostTextField.text
        val port = (generalOption.portTextField.value ?: 5900) as Int
        var authentication = Authentication.Companion.No
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


        return Host(
            name = name,
            protocol = protocol,
            host = host,
            port = port,
            authentication = authentication,
            proxy = proxy,
            sort = System.currentTimeMillis(),
            remark = generalOption.remarkTextArea.text,
            options = Options.Default.copy(
                encoding = terminalOption.charsetComboBox.selectedItem?.toString() ?: "ISO-8859-1"
            )
        )
    }

    fun setHost(host: Host) {
        generalOption.portTextField.value = host.port
        generalOption.nameTextField.text = host.name
        generalOption.hostTextField.text = host.host
        generalOption.remarkTextArea.text = host.remark
        generalOption.authenticationTypeComboBox.selectedItem = host.authentication.type
        if (host.authentication.type == AuthenticationType.Password) {
            generalOption.passwordTextField.text = host.authentication.password
        }

        terminalOption.charsetComboBox.selectedItem = StringUtils.defaultIfBlank(host.options.encoding, "ISO-8859-1")

        proxyOption.proxyTypeComboBox.selectedItem = host.proxy.type
        proxyOption.proxyHostTextField.text = host.proxy.host
        proxyOption.proxyPasswordTextField.text = host.proxy.password
        proxyOption.proxyUsernameTextField.text = host.proxy.username
        proxyOption.proxyPortTextField.value = host.proxy.port
        proxyOption.proxyAuthenticationTypeComboBox.selectedItem = host.proxy.authenticationType


    }

    fun validateFields(): Boolean {
        val host = getHost()

        // general
        if (validateField(generalOption.nameTextField)
            || validateField(generalOption.hostTextField)
        ) {
            return false
        }

        if (host.authentication.type == AuthenticationType.Password) {
            if (validateField(generalOption.passwordTextField)) {
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

    protected inner class GeneralOption : JPanel(BorderLayout()), Option {
        val portTextField = PortSpinner(5900)
        val nameTextField = OutlineTextField(128)
        val hostTextField = OutlineTextField(255)
        val passwordTextField = OutlinePasswordField(255)
        val remarkTextArea = FixedLengthTextArea(512)
        val authenticationTypeComboBox = FlatComboBox<AuthenticationType>()

        init {
            initView()
            initEvents()
        }

        private fun initView() {
            add(getCenterComponent(), BorderLayout.CENTER)

            authenticationTypeComboBox.renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    val text = (value as? AuthenticationType)?.getDisplayName() ?: value?.toString().orEmpty()
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

            authenticationTypeComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    passwordTextField.isEnabled = authenticationTypeComboBox.selectedItem == AuthenticationType.Password
                }
            }
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


}
