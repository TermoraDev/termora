package app.termora.plugin.internal.cli

import app.termora.OptionsPane
import app.termora.SettingsOptionExtension

class CliSettingsOptionExtension private constructor() : SettingsOptionExtension {
    companion object {
        val instance = CliSettingsOptionExtension()
    }

    override fun createSettingsOption(): OptionsPane.Option = CliWhitelistOption()
}
