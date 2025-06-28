package app.termora.plugins.geo

import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.Plugin
import app.termora.tree.HostTreeShowMoreEnableExtension
import app.termora.tree.SimpleTreeCellRendererExtension

class GeoPlugin : Plugin {
    private val support = ExtensionSupport()

    init {
        support.addExtension(SimpleTreeCellRendererExtension::class.java) { GeoSimpleTreeCellRendererExtension.instance }
        support.addExtension(HostTreeShowMoreEnableExtension::class.java) { GeoHostTreeShowMoreEnableExtension.instance }
    }


    override fun getAuthor(): String {
        return "TermoraDev"
    }


    override fun getName(): String {
        return "Geo"
    }


    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}