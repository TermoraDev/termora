package app.termora.account

import app.termora.OptionsPane
import app.termora.SettingsOptionExtension

class AccountSettingsOptionExtension private constructor() : SettingsOptionExtension {
    companion object {
        val instance by lazy { AccountSettingsOptionExtension() }
    }

    override fun createSettingsOption(): OptionsPane.Option {
        return AccountOption()
    }
}