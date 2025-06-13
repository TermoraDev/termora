package app.termora.sftp.internal.local

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin
import app.termora.protocol.ProtocolProviderExtension

internal class LocalPlugin : InternalPlugin() {
    init {
        support.addExtension(ProtocolProviderExtension::class.java) { LocalProtocolProviderExtension.instance }
    }

    override fun getName(): String {
        return "Local Transfer"
    }

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }

}