package app.termora.plugin.internal.extension

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin

internal class DynamicExtensionPlugin  : InternalPlugin() {


    override fun getName(): String {
        return "Dynamic Extension"
    }



    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return DynamicExtensionHandler.getInstance().getExtensions(clazz)
    }
}