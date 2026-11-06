package app.termora.plugin.internal.ssh

import app.termora.TerminalTabbedContextMenuExtension
import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProviderExtension

internal class SSHInternalPlugin : InternalPlugin() {
    init {
        support.addExtension(app.termora.SettingsOptionExtension::class.java) {
            object : app.termora.SettingsOptionExtension {
                override fun createSettingsOption() = app.termora.ConnectionSettings()
            }
        }
        support.addExtension(ProtocolProviderExtension::class.java) { SSHProtocolProviderExtension.instance }
        support.addExtension(ProtocolHostPanelExtension::class.java) { SSHProtocolHostPanelExtension.instance }
        support.addExtension(TerminalTabbedContextMenuExtension::class.java) { SftpCommandTerminalTabbedContextMenuExtension.instance }
        support.addExtension(TerminalTabbedContextMenuExtension::class.java) { CloneSessionTerminalTabbedContextMenuExtension.instance }
    }

    override fun getName(): String {
        return "SSH Protocol"
    }


    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}