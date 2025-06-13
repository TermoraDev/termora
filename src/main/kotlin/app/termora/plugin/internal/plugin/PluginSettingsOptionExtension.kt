package app.termora.plugin.internal.plugin

import app.termora.OptionsPane
import app.termora.SettingsOptionExtension

class PluginSettingsOptionExtension private constructor() : SettingsOptionExtension {
    companion object {
        val instance by lazy { PluginSettingsOptionExtension() }
    }

    override fun createSettingsOption(): OptionsPane.Option {
        return PluginOption()
    }
}