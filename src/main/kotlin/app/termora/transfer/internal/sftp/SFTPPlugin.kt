package app.termora.transfer.internal.sftp

import app.termora.FrameExtension
import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin
import app.termora.protocol.ProtocolProviderExtension
import app.termora.transfer.TransportContextMenuExtension

internal class SFTPPlugin : InternalPlugin() {
    init {
        support.addExtension(ProtocolProviderExtension::class.java) { SFTPProtocolProviderExtension.instance }
        support.addExtension(FrameExtension::class.java) { SFTPFrameExtension.instance }
        support.addExtension(TransportContextMenuExtension::class.java) { RmrfTransportContextMenuExtension.instance }
        support.addExtension(TransportContextMenuExtension::class.java) { CompressTransportContextMenuExtension.instance }
        support.addExtension(TransportContextMenuExtension::class.java) { ExtractTransportContextMenuExtension.instance }
    }

    override fun getName(): String {
        return "Local Transfer"
    }

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }

}