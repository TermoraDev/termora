package app.termora.plugin.internal.local

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProviderExtension

internal class LocalInternalPlugin : InternalPlugin() {
    init {
        support.addExtension(ProtocolProviderExtension::class.java) { LocalProtocolProviderExtension.instance }
        support.addExtension(ProtocolHostPanelExtension::class.java) { LocalProtocolHostPanelExtension.instance }
    }

    override fun getName(): String {
        return "Local Protocol"
    }



    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}