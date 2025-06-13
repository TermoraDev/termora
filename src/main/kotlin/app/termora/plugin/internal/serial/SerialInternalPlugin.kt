package app.termora.plugin.internal.serial

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProviderExtension

internal class SerialInternalPlugin : InternalPlugin() {
    init {
        support.addExtension(ProtocolProviderExtension::class.java) { SerialProtocolProviderExtension.instance }
        support.addExtension(ProtocolHostPanelExtension::class.java) { SerialProtocolHostPanelExtension.instance }
    }


    override fun getName(): String {
        return "Serial Protocol"
    }


    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}