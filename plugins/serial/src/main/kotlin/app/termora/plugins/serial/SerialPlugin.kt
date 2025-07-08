package app.termora.plugins.serial

import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.Plugin
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProviderExtension

internal class SerialPlugin : Plugin {
    private val support = ExtensionSupport()

    override fun getAuthor(): String {
        return "TermoraDev"
    }

    init {
        support.addExtension(ProtocolProviderExtension::class.java) { SerialProtocolProviderExtension.instance }
        support.addExtension(ProtocolHostPanelExtension::class.java) { SerialProtocolHostPanelExtension.instance }
    }


    override fun getName(): String {
        return "Serial Comm"
    }


    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}