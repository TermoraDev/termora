package app.termora.plugins.sync

import app.termora.OptionsPane
import app.termora.SettingsOptionExtension

class SyncSettingsOptionExtension private constructor() : SettingsOptionExtension {
    companion object {
        val instance by lazy { SyncSettingsOptionExtension() }
    }

    override fun createSettingsOption(): OptionsPane.Option {
        return CloudSyncOption()
    }
}