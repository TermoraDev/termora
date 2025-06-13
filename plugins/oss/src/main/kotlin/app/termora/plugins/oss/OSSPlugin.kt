package app.termora.plugins.oss

import app.termora.DynamicIcon
import app.termora.I18n
import app.termora.Icons
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.PaidPlugin
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProviderExtension

class OSSPlugin : PaidPlugin {
    private val support = ExtensionSupport()

    init {
        support.addExtension(ProtocolProviderExtension::class.java) { OSSProtocolProviderExtension.instance }
        support.addExtension(ProtocolHostPanelExtension::class.java) { OSSProtocolHostPanelExtension.instance }
    }

    override fun getAuthor(): String {
        return "TermoraDev"
    }


    override fun getName(): String {
        return "Alibaba OSS"
    }


    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}