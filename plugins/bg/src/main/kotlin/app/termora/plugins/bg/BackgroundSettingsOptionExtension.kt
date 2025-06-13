package app.termora.plugins.bg

import app.termora.OptionsPane
import app.termora.SettingsOptionExtension

class BackgroundSettingsOptionExtension private constructor(): SettingsOptionExtension {
    companion object {
        val instance by lazy { BackgroundSettingsOptionExtension() }
    }

    override fun createSettingsOption(): OptionsPane.Option {
        return BackgroundOption()
    }
}