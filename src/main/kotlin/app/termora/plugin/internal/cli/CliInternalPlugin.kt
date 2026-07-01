package app.termora.plugin.internal.cli

import app.termora.SettingsOptionExtension
import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin

internal class CliInternalPlugin : InternalPlugin() {
    init {
        support.addExtension(SettingsOptionExtension::class.java) { CliSettingsOptionExtension.instance }
    }

    override fun getName(): String = "CLI"

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }
}
