package app.termora

import app.termora.keymgr.KeyManager
import app.termora.keymgr.OhKeyPair
import app.termora.localshell.LocalShellId
import app.termora.localshell.LocalShellProvider

class EditHostOptionsPane(private val host: Host) : HostOptionsPane() {
    init {
        generalOption.portTextField.value = host.port
        generalOption.nameTextField.text = host.name
        generalOption.protocolTypeComboBox.selectedItem = host.protocol
        generalOption.usernameTextField.text = host.username
        generalOption.hostTextField.text = host.host
        generalOption.passwordTextField.text = host.authentication.password
        generalOption.remarkTextArea.text = host.remark
        generalOption.authenticationTypeComboBox.selectedItem = host.authentication.type
        if (host.authentication.type == AuthenticationType.PublicKey) {
            val ohKeyPair = KeyManager.instance.getOhKeyPair(host.authentication.password)
            if (ohKeyPair != null) {
                generalOption.publicKeyTextField.text = ohKeyPair.name
                generalOption.publicKeyTextField.putClientProperty(OhKeyPair::class, ohKeyPair)
            }
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

        val provider = LocalShellProvider.makeProviderByIdentificationString(host.options.localShell) ?: LocalShellProvider.FollowGlobal

        if (provider is LocalShellProvider.CustomCommandLineArgs) {
            terminalOption.localShellProviderComboBox.isEditable = true
            terminalOption.localShellProviderComboBox.selectedItem = provider.commandLine.commandLineString
        } else {
            terminalOption.localShellProviderComboBox.selectedItem = provider
        }

        tunnelingOption.tunnelings.addAll(host.tunnelings)
    }

    override fun getHost(): Host {
        val newHost = super.getHost()
        return host.copy(
            name = newHost.name,
            protocol = newHost.protocol,
            host = newHost.host,
            port = newHost.port,
            username = newHost.username,
            authentication = newHost.authentication,
            proxy = newHost.proxy,
            remark = newHost.remark,
            updateDate = System.currentTimeMillis(),
            options = newHost.options,
            tunnelings = newHost.tunnelings,
        )
    }
}