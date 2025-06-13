package app.termora.plugins.sync

import app.termora.database.DatabaseChangedExtension

class SyncDatabaseChangedExtension : DatabaseChangedExtension {
    companion object {
        val instance by lazy { SyncDatabaseChangedExtension() }
    }


    override fun onDataChanged(
        id: String,
        type: String,
        action: DatabaseChangedExtension.Action,
        source: DatabaseChangedExtension.Source
    ) {
        SyncManager.getInstance().triggerOnChanged()
    }
}