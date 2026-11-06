package app.termora.plugin.internal.ssh

import app.termora.*
import app.termora.account.AccountOwner
import app.termora.highlight.KeywordHighlight
import app.termora.keymgr.KeyManager
import app.termora.keymgr.KeyManagerDialog
import app.termora.plugin.internal.AltKeyModifier
import app.termora.plugin.internal.BasicProxyOption
import app.termora.plugin.internal.BasicTerminalOption
import app.termora.plugin.internal.telnet.TelnetHostOptionsPane.Backspace
import app.termora.tree.Filter
import app.termora.tree.HostTreeNode
import app.termora.tree.NewHostTreeDialog
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatComboBox
import com.formdev.flatlaf.ui.FlatTextBorder
import com.formdev.flatlaf.util.SystemInfo
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.lang3.StringUtils
import org.eclipse.jgit.internal.transport.sshd.agent.connector.PageantConnector
import org.eclipse.jgit.internal.transport.sshd.agent.connector.UnixDomainSocketConnector
import org.eclipse.jgit.internal.transport.sshd.agent.connector.WinPipeConnector
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

@Suppress("CascadeIf")
internal class SSHHostOptionsPane(private val accountOwner: AccountOwner) : OptionsPane() {
    private val tunnelingOption = SSHTunnelingOption()
    private val generalOption = SSHGeneralOption(accountOwner)
    private val proxyOption = BasicProxyOption()
    private val terminalOption = BasicTerminalOption().apply {
        showCharsetComboBox = true
        showLoginScripts = true
        showBackspaceComboBox = true
        showEnvironmentTextArea = true
        showStartupCommandTextField = true
        showHeartbeatIntervalTextField = true
        showTimeoutTextField = true
        showHighlightSet = true
        accountOwner = this@SSHHostOptionsPane.accountOwner
        init()
    }
    private val jumpHostsOption = SSHJumpHostsOption()
    private val sftpOption = SSHSFTPOption()
    private val owner: Window get() = SwingUtilities.getWindowAncestor(this)

    init {
        addOption(generalOption)
        addOption(proxyOption)
        if (!ConnectionSettings.unifiedTunnelsOnly) addOption(tunnelingOption)
        addOption(jumpHostsOption)
        addOption(terminalOption)
        addOption(sftpOption)

    }


    fun getHost(): Host {
        val name = generalOption.nameTextField.text
        val protocol = SSHProtocolProvider.PROTOCOL
        val host = generalOption.hostTextField.text
        val port = (generalOption.portTextField.value ?: 22) as Int
        var authentication = Authentication.Companion.No
        var proxy = Proxy.Companion.No
        val authenticationType = generalOption.authenticationTypeComboBox.selectedItem as AuthenticationType

        if (authenticationType == AuthenticationType.Password) {
            authentication = authentication.copy(
                type = authenticationType,
                password = String(generalOption.passwordTextField.password)
            )
        } else if (authenticationType == AuthenticationType.PublicKey) {
            authentication = authentication.copy(
                type = authenticationType,
                password = generalOption.publicKeyComboBox.selectedItem?.toString() ?: StringUtils.EMPTY
            )
        } else if (authenticationType == AuthenticationType.SSHAgent) {
            authentication = authentication.copy(
                type = authenticationType,
                password = generalOption.sshAgentComboBox.selectedItem?.toString() ?: StringUtils.EMPTY
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
            heartbeatInterval = (terminalOption.heartbeatIntervalTextField.value ?: 30) as Int,
            jumpHosts = jumpHostsOption.jumpHosts.map { it.id },
            serialComm = serialComm,
            sftpDefaultDirectory = sftpOption.defaultDirectoryField.text,
            enableX11Forwarding = tunnelingOption.x11ForwardingCheckBox.isSelected,
            x11Forwarding = tunnelingOption.x11ServerTextField.text,
            loginScripts = terminalOption.loginScripts,
            extras = mutableMapOf(
                "backspace" to (terminalOption.backspaceComboBox.selectedItem as Backspace).name,
                "altModifier" to (terminalOption.altModifierComboBox.selectedItem?.toString()
                    ?: AltKeyModifier.EightBit.name),
                "keywordHighlightSetId" to ((terminalOption.highlightSetComboBox.selectedItem as? KeywordHighlight)?.id
                    ?: "-1"),
                "timeout" to (terminalOption.timeoutTextField.value ?: 60).toString(),
                "forwardAgent" to tunnelingOption.forwardAgentCheckBox.isSelected.toString(),
                "autoReconnect" to generalOption.autoReconnect.isSelected.toString(),
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
            tunnelings = tunnelingOption.tunnelings
        )
    }

    fun setHost(host: Host) {
        generalOption.setHostMode = true
        generalOption.autoReconnect.isSelected = host.options.extras["autoReconnect"].toBoolean()
        generalOption.portTextField.value = host.port
        generalOption.nameTextField.text = host.name
        generalOption.usernameTextField.text = host.username
        generalOption.hostTextField.text = host.host
        generalOption.remarkTextArea.text = host.remark
        generalOption.authenticationTypeComboBox.selectedItem = host.authentication.type
        if (host.authentication.type == AuthenticationType.Password) {
            generalOption.passwordTextField.text = host.authentication.password
        } else if (host.authentication.type == AuthenticationType.PublicKey) {
            generalOption.publicKeyComboBox.selectedItem = host.authentication.password
        } else if (host.authentication.type == AuthenticationType.SSHAgent) {
            generalOption.sshAgentComboBox.selectedItem = host.authentication.password
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
        terminalOption.heartbeatIntervalTextField.value = host.options.heartbeatInterval
        terminalOption.loginScripts.addAll(host.options.loginScripts)
        val altModifier = host.options.extras["altModifier"] ?: AltKeyModifier.EightBit.name
        terminalOption.altModifierComboBox.selectedItem = runCatching { AltKeyModifier.valueOf(altModifier) }
            .getOrNull() ?: AltKeyModifier.EightBit


        terminalOption.backspaceComboBox.selectedItem =
            Backspace.valueOf(host.options.extras["backspace"] ?: Backspace.Delete.name)

        val timeout = host.options.extras["timeout"] ?: "60"
        terminalOption.timeoutTextField.value = timeout.toIntOrNull() ?: 60


        val keywordHighlightSetId = host.options.extras["keywordHighlightSetId"]
        for (i in 0 until terminalOption.highlightSetComboBox.itemCount) {
            val item = terminalOption.highlightSetComboBox.getItemAt(i)
            if (item.id == keywordHighlightSetId) {
                terminalOption.highlightSetComboBox.selectedItem = item
                break
            }
        }


        tunnelingOption.tunnelings.addAll(host.tunnelings)
        tunnelingOption.x11ForwardingCheckBox.isSelected = host.options.enableX11Forwarding
        tunnelingOption.x11ServerTextField.text = StringUtils.defaultIfBlank(host.options.x11Forwarding, "localhost:0")
        tunnelingOption.forwardAgentCheckBox.isSelected = host.options.extras["forwardAgent"]?.toBoolean() ?: false

        if (host.options.jumpHosts.isNotEmpty()) {
            val hosts = HostManager.getInstance().hosts().associateBy { it.id }
            for (id in host.options.jumpHosts) {
                jumpHostsOption.jumpHosts.add(hosts[id] ?: continue)
            }
        }

        jumpHostsOption.filter = { it.id != host.id }

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

        if (StringUtils.equalsIgnoreCase(host.protocol, SSHProtocolProvider.PROTOCOL)) {
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

        // tunnel
        if (tunnelingOption.x11ForwardingCheckBox.isSelected) {
            if (validateField(tunnelingOption.x11ServerTextField)) {
                return false
            }
            val segments = tunnelingOption.x11ServerTextField.text.split(":")
            if (segments.size != 2 || segments[1].toIntOrNull() == null) {
                setOutlineError(tunnelingOption.x11ServerTextField)
                return false
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

}
