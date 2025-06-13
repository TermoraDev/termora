package app.termora.plugins.geo

import app.termora.ApplicationRunnerExtension
import app.termora.FrameExtension
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.Plugin
import app.termora.tree.HostTreeShowMoreEnableExtension
import app.termora.tree.SimpleTreeCellRendererExtension

class GeoPlugin : Plugin {
    private val support = ExtensionSupport()

    init {
        support.addExtension(ApplicationRunnerExtension::class.java) { GeoApplicationRunnerExtension.instance }
        support.addExtension(SimpleTreeCellRendererExtension::class.java) { GeoSimpleTreeCellRendererExtension.instance }
        support.addExtension(HostTreeShowMoreEnableExtension::class.java) { GeoHostTreeShowMoreEnableExtension.instance }
        support.addExtension(FrameExtension::class.java) { GeoFrameExtension.instance }
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