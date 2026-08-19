package app.termora.plugins.obs

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

class OBSHostOptionsPane : OptionsPane() {
    private val generalOption = GeneralOption()
    private val proxyOption = BasicProxyOption(listOf(ProxyType.HTTP))
    private val sftpOption = SFTPOption()

    init {
        addOption(generalOption)
        addOption(proxyOption)
        addOption(sftpOption)

    }

    fun getHost(): Host {
        val name = generalOption.nameTextField.text
        val protocol = OBSProtocolProvider.PROTOCOL
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
                "oss.delimiter" to StringUtils.defaultIfBlank(generalOption.delimiterTextField.text, "/"),
//                "oss.region" to generalOption.regionComboBox.selectedItem as String,
            )
        )

        return Host(
            name = name,
            protocol = protocol,
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
        generalOption.remarkTextArea.text = host.remark
        generalOption.passwordTextField.text = host.authentication.password
        generalOption.delimiterTextField.text = host.options.extras["oss.delimiter"] ?: "/"
//        generalOption.regionComboBox.selectedItem = host.options.extras["oss.region"] ?: StringUtils.EMPTY

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
        if (validateField(generalOption.nameTextField)) {
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

    private fun setOutlineError(c: JComponent) {
        selectOptionJComponent(c)
        c.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR)
        c.requestFocusInWindow()
    }


    private inner class GeneralOption : JPanel(BorderLayout()), Option {
        val nameTextField = OutlineTextField(128)
        val usernameTextField = OutlineTextField(128)
        val passwordTextField = OutlinePasswordField(255)
        val remarkTextArea = FixedLengthTextArea(512)

        val delimiterTextField = OutlineTextField(128)

        init {
            initView()
            initEvents()
        }

        private fun initView() {

            /*regionComboBox.isEditable = true


            // 亚太-中国
            regionComboBox.addItem("oss-cn-hangzhou")
            regionComboBox.addItem("oss-cn-shanghai")
            regionComboBox.addItem("oss-cn-nanjing")
            regionComboBox.addItem("oss-cn-qingdao")
            regionComboBox.addItem("oss-cn-beijing")
            regionComboBox.addItem("oss-cn-zhangjiakou")
            regionComboBox.addItem("oss-cn-huhehaote")
            regionComboBox.addItem("oss-cn-wulanchabu")
            regionComboBox.addItem("oss-cn-shenzhen")
            regionComboBox.addItem("oss-cn-heyuan")
            regionComboBox.addItem("oss-cn-guangzhou")
            regionComboBox.addItem("oss-cn-chengdu")
            regionComboBox.addItem("oss-cn-hongkong")

            // 亚太-其他
            regionComboBox.addItem("oss-ap-northeast-1")
            regionComboBox.addItem("oss-ap-northeast-2")
            regionComboBox.addItem("oss-ap-southeast-1")
            regionComboBox.addItem("oss-ap-southeast-3")
            regionComboBox.addItem("oss-ap-southeast-5")
            regionComboBox.addItem("oss-ap-southeast-6")
            regionComboBox.addItem("oss-ap-southeast-7")

            // 欧洲与美洲
            regionComboBox.addItem("oss-eu-central-1")
            regionComboBox.addItem("oss-eu-west-1")
            regionComboBox.addItem("oss-us-west-1")
            regionComboBox.addItem("oss-us-east-1")
            regionComboBox.addItem("oss-na-south-1")

            // 中东
            regionComboBox.addItem("oss-me-east-1")
            regionComboBox.addItem("oss-me-central-1")

            endpointTextField.isEditable = false*/



            delimiterTextField.text = "/"
            delimiterTextField.isEditable = false
            add(getCenterComponent(), BorderLayout.CENTER)
        }

        private fun initEvents() {

            addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    SwingUtilities.invokeLater { nameTextField.requestFocusInWindow() }
                    removeComponentListener(this)
                }
            })

            /*regionComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    endpointTextField.text = "${regionComboBox.selectedItem}.aliyuncs.com"
                }
            }*/
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

//                .add("Region:").xy(1, rows)
//                .add(regionComboBox).xyw(3, rows, 5).apply { rows += step }

//                .add("Endpoint:").xy(1, rows)
//                .add(endpointTextField).xyw(3, rows, 5).apply { rows += step }

                .add("${I18n.getString("termora.object-storage.secret-id")}:").xy(1, rows)
                .add(usernameTextField).xyw(3, rows, 5).apply { rows += step }

                .add("${I18n.getString("termora.object-storage.secret-key")}:").xy(1, rows)
                .add(passwordTextField).xyw(3, rows, 5).apply { rows += step }

                .add("${I18n.getString("termora.object-storage.delimiter")}:").xy(1, rows)
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
