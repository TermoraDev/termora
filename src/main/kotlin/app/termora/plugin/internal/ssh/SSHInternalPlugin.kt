package app.termora.plugin.internal.ssh

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProviderExtension

internal class SSHInternalPlugin : InternalPlugin() {
    init {
        support.addExtension(ProtocolProviderExtension::class.java) { SSHProtocolProviderExtension.instance }
        support.addExtension(ProtocolHostPanelExtension::class.java) { SSHProtocolHostPanelExtension.instance }
    }

    override fun getName(): String {
        return "SSH Protocol"
    }


    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}