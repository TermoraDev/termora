package app.termora.plugin.internal.plugin

import app.termora.ApplicationRunnerExtension
import app.termora.SettingsOptionExtension
import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin
import app.termora.plugin.marketplace.MarketplaceManager

internal class PluginInternalPlugin : InternalPlugin() {
    init {
        support.addExtension(SettingsOptionExtension::class.java) { PluginSettingsOptionExtension.instance }
        support.addExtension(ApplicationRunnerExtension::class.java) { MarketplaceManager.MarketplaceManagerReady.instance }
    }

    override fun getName(): String {
        return "Plugin"
    }


    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}