package app.termora.plugins.s3

import app.termora.*
import app.termora.plugin.internal.BasicProxyOption
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.ui.FlatTextBorder
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.lang3.StringUtils
import java.awt.BorderLayout
import java.awt.KeyboardFocusManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*

class S3HostOptionsPane : OptionsPane() {
    private val generalOption = GeneralOption()
    private val proxyOption = BasicProxyOption()
    private val sftpOption = SFTPOption()

    init {
        addOption(generalOption)
        addOption(proxyOption)
        addOption(sftpOption)

    }


    fun getHost(): Host {
        val name = generalOption.nameTextField.text
        val protocol = S3ProtocolProvider.PROTOCOL
        val host = generalOption.hostTextField.text
        val port = 0
        var authentication = Authentication.Companion.No
        var proxy = Proxy.Companion.No
        val authenticationType = AuthenticationType.Password

        authentication = authentication.copy(
            type = authenticationType,
            password = String(generalOption.passwordTextField.password)
        )


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


        val options = Options.Default.copy(
            sftpDefaultDirectory = sftpOption.defaultDirectoryField.text,
            extras = mutableMapOf(
                "s3.region" to generalOption.regionTextField.text,
                "s3.delimiter" to generalOption.delimiterTextField.text,
            )
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
        generalOption.nameTextField.text = host.name
        generalOption.usernameTextField.text = host.username
        generalOption.hostTextField.text = host.host
        generalOption.remarkTextArea.text = host.remark
        generalOption.passwordTextField.text = host.authentication.password
        generalOption.regionTextField.text = host.options.extras["s3.region"] ?: StringUtils.EMPTY
        generalOption.delimiterTextField.text = host.options.extras["s3.delimiter"] ?: StringUtils.EMPTY

        proxyOption.proxyTypeComboBox.selectedItem = host.proxy.type
        proxyOption.proxyHostTextField.text = host.proxy.host
        proxyOption.proxyPasswordTextField.text = host.proxy.password
        proxyOption.proxyUsernameTextField.text = host.proxy.username
        proxyOption.proxyPortTextField.value = host.proxy.port
        proxyOption.proxyAuthenticationTypeComboBox.selectedItem = host.proxy.authenticationType


        sftpOption.defaultDirectoryField.text = host.options.sftpDefaultDirectory
    }

    fun validateFields(): Boolean {
        val host = getHost()

        // general
        if (validateField(generalOption.nameTextField)
            || validateField(generalOption.hostTextField)
        ) {
            return false
        }

        if (validateField(generalOption.usernameTextField)) {
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


    private inner class GeneralOption : JPanel(BorderLayout()), Option {
        val nameTextField = OutlineTextField(128)
        val usernameTextField = OutlineTextField(128)
        val hostTextField = OutlineTextField(255)
        val passwordTextField = OutlinePasswordField(255)
        val remarkTextArea = FixedLengthTextArea(512)
        val regionTextField = OutlineTextField(128)
        val delimiterTextField = OutlineTextField(128)

        init {
            initView()
            initEvents()
        }

        private fun initView() {
            delimiterTextField.text = "/"
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

                .add("Endpoint:").xy(1, rows)
                .add(hostTextField).xyw(3, rows, 5).apply { rows += step }

                .add("AccessKey:").xy(1, rows)
                .add(usernameTextField).xyw(3, rows, 5).apply { rows += step }

                .add("SecureKey:").xy(1, rows)
                .add(passwordTextField).xyw(3, rows, 5).apply { rows += step }

                .add("Region:").xy(1, rows)
                .add(regionTextField).xyw(3, rows, 5).apply { rows += step }

                .add("Delimiter:").xy(1, rows)
                .add(delimiterTextField).xyw(3, rows, 5).apply { rows += step }

                .add("${I18n.getString("termora.new-host.general.remark")}:").xy(1, rows)
                .add(JScrollPane(remarkTextArea).apply { border = FlatTextBorder() })
                .xyw(3, rows, 5).apply { rows += step }

                .build()


            return panel
        }

    }


    private inner class SFTPOption : JPanel(BorderLayout()), Option {
        val defaultDirectoryField = OutlineTextField(255)


        init {
            initView()
            initEvents()
        }

        private fun initView() {
            add(getCenterComponent(), BorderLayout.CENTER)
        }

        private fun initEvents() {

        }


        override fun getIcon(isSelected: Boolean): Icon {
            return Icons.folder
        }

        override fun getTitle(): String {
            return I18n.getString("termora.transport.sftp")
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
                .add("${I18n.getString("termora.settings.sftp.default-directory")}:").xy(1, rows)
                .add(defaultDirectoryField).xy(3, rows).apply { rows += step }
                .build()


            return panel
        }
    }


}