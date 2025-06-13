package app.termora.plugin.internal.rdp

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProviderExtension

internal class RDPInternalPlugin : InternalPlugin() {
    init {
        support.addExtension(ProtocolProviderExtension::class.java) { RDPProtocolProviderExtension.instance }
        support.addExtension(ProtocolHostPanelExtension::class.java) { RDPProtocolHostPanelExtension.instance }
    }

    override fun getName(): String {
        return "RDP Protocol"
    }


    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}