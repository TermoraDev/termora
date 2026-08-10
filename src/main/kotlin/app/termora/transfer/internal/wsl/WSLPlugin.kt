package app.termora.transfer.internal.wsl

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin
import app.termora.protocol.ProtocolProviderExtension

internal class WSLPlugin : InternalPlugin() {
    init {
        support.addExtension(ProtocolProviderExtension::class.java) { WSLProtocolProviderExtension.instance }
    }

    override fun getName(): String {
        return "WSL Transfer"
    }

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }
}
