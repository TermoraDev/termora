package app.termora.plugin.internal.sftppty

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin
import app.termora.protocol.ProtocolProviderExtension

internal class SFTPPtyInternalPlugin : InternalPlugin() {
    init {
        support.addExtension(ProtocolProviderExtension::class.java) { SFTPPtyProtocolProviderExtension.instance }
    }

    override fun getName(): String {
        return "SFTP Pty Protocol"
    }



    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }

}