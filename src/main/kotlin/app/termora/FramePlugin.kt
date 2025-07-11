package app.termora

import app.termora.database.DatabaseChangedExtension
import app.termora.database.DatabasePropertiesChangedExtension
import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin

internal class FramePlugin : InternalPlugin() {
    init {
        support.addExtension(DatabasePropertiesChangedExtension::class.java) { KeymapRefresher.getInstance() }
        support.addExtension(DatabaseChangedExtension::class.java) { KeymapRefresher.getInstance() }
    }

    override fun getName(): String {
        return "Frame"
    }

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }
}