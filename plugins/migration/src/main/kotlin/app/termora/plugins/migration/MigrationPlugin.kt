package app.termora.plugins.migration

import app.termora.ApplicationRunnerExtension
import app.termora.DynamicIcon
import app.termora.I18n
import app.termora.Icons
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.Plugin

class MigrationPlugin : Plugin {
    private val support = ExtensionSupport()

    init {
        support.addExtension(ApplicationRunnerExtension::class.java) { MigrationApplicationRunnerExtension.instance }
    }

    override fun getAuthor(): String {
        return "TermoraDev"
    }


    override fun getName(): String {
        return "Migration"
    }


    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}