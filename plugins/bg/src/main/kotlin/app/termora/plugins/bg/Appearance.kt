package app.termora.plugins.bg

import app.termora.EnableManager
import app.termora.database.DatabaseManager

object Appearance {
    private val enableManager get() = EnableManager.getInstance()
    private val appearance get() = DatabaseManager.getInstance().appearance

    var backgroundImage: String
        get() = enableManager.getFlag("Plugins.bg.backgroundImage", appearance.backgroundImage)
        set(value) {
            enableManager.setFlag("Plugins.bg.backgroundImage", value)
        }

    var interval: Int
        get() = enableManager.getFlag("Plugins.bg.interval", 360)
        set(value) {
            enableManager.setFlag("Plugins.bg.interval", value)
        }
}