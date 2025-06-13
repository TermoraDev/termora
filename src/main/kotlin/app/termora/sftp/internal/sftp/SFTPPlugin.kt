package app.termora.sftp.internal.sftp

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin
import app.termora.protocol.ProtocolProviderExtension

internal class SFTPPlugin : InternalPlugin() {
    init {
        support.addExtension(ProtocolProviderExtension::class.java) { SFTPProtocolProviderExtension.instance }
    }

    override fun getName(): String {
        return "Transfer"
    }

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }

}