package app.termora.plugin.internal.windows

import app.termora.SettingsOptionExtension
import app.termora.plugin.Extension
import app.termora.plugin.Plugin

class WindowsIntegrationPlugin : Plugin {
    override fun getAuthor(): String = "Termora"
    override fun getName(): String = "Windows Integration"

    @Suppress("UNCHECKED_CAST")
    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        if (SettingsOptionExtension::class.java.isAssignableFrom(clazz)) {
            return listOf(WindowsSettingsExtension() as T)
        }
        return emptyList()
    }
}
