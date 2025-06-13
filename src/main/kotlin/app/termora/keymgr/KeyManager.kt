package app.termora.keymgr

import app.termora.Application.ohMyJson
import app.termora.ApplicationScope
import app.termora.DeleteDataManager
import app.termora.account.AccountOwner
import app.termora.database.Data
import app.termora.database.DataType
import app.termora.database.DatabaseManager

class KeyManager private constructor() {
    companion object {
        fun getInstance(): KeyManager {
            return ApplicationScope.forApplicationScope().getOrCreate(KeyManager::class) { KeyManager() }
        }
    }

    private val databaseManager get() = DatabaseManager.getInstance()


    fun addOhKeyPair(keyPair: OhKeyPair, accountOwner: AccountOwner) {
        if (keyPair == OhKeyPair.empty) {
            return
        }

        databaseManager.saveAndIncrementVersion(
            Data(
                id = keyPair.id,
                ownerId = accountOwner.id,
                ownerType = accountOwner.type.name,
                type = DataType.KeyPair.name,
                data = ohMyJson.encodeToString(keyPair),
            )
        )
    }

    fun removeOhKeyPair(id: String) {
        databaseManager.delete(id, DataType.KeyPair.name)
        DeleteDataManager.getInstance().removeKeyPair(id)
    }

    fun getOhKeyPairs(): List<OhKeyPair> {
        return databaseManager.data<OhKeyPair>(DataType.KeyPair)
    }

    fun getOhKeyPairs(ownerId: String): List<OhKeyPair> {
        return databaseManager.data(DataType.KeyPair, ownerId)
    }

    fun getOhKeyPair(id: String): OhKeyPair? {
        val data = databaseManager.data(id) ?: return null
        if (data.type != DataType.KeyPair.name) {
            return null
        }
        return ohMyJson.decodeFromString(data.data)
    }

}