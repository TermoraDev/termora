package app.termora.plugins.bg

import app.termora.ApplicationRunnerExtension
import app.termora.GlassPaneAwareExtension
import app.termora.GlassPaneExtension
import app.termora.SettingsOptionExtension
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.Plugin

class BGPlugin : Plugin {
    private val support = ExtensionSupport()

    init {
        support.addExtension(GlassPaneExtension::class.java) { BGGlassPaneExtension.instance }
        support.addExtension(SettingsOptionExtension::class.java) { BackgroundSettingsOptionExtension.instance }
        support.addExtension(ApplicationRunnerExtension::class.java) { BackgroundManager.getInstance() }
        support.addExtension(GlassPaneAwareExtension::class.java) { BackgroundManager.getInstance() }
    }

    override fun getAuthor(): String {
        return "TermoraDev"
    }


    override fun getName(): String {
        return "Customize Background"
    }


    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}