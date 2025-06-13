package app.termora.plugins.sync

import app.termora.SettingsOptionExtension
import app.termora.database.DatabaseChangedExtension
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.Plugin

class SyncPlugin : Plugin {
    private val support = ExtensionSupport()

    init {
        support.addExtension(SettingsOptionExtension::class.java) { SyncSettingsOptionExtension.instance }
        support.addExtension(DatabaseChangedExtension::class.java) { SyncDatabaseChangedExtension.instance }
    }

    override fun getAuthor(): String {
        return "TermoraDev"
    }


    override fun getName(): String {
        return "Sync"
    }

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}