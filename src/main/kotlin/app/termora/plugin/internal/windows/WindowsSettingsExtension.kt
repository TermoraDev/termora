package app.termora.plugin.internal.windows

import app.termora.OptionsPane
import app.termora.SettingsOptionExtension

class WindowsSettingsExtension : SettingsOptionExtension {
    override fun createSettingsOption(): OptionsPane.Option {
        return WindowsSettingsOption()
    }
}
