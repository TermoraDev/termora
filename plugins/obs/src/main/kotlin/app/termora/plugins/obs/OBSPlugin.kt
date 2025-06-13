package app.termora.plugins.obs

import app.termora.DynamicIcon
import app.termora.I18n
import app.termora.Icons
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.PaidPlugin
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProviderExtension

class OBSPlugin : PaidPlugin {
    private val support = ExtensionSupport()

    init {
        support.addExtension(ProtocolProviderExtension::class.java) { OBSProtocolProviderExtension.instance }
        support.addExtension(ProtocolHostPanelExtension::class.java) { OBSProtocolHostPanelExtension.instance }
    }

    override fun getAuthor(): String {
        return "TermoraDev"
    }


    override fun getName(): String {
        return "Huawei OBS"
    }


    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}