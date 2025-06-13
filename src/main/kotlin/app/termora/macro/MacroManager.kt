package app.termora.macro

import app.termora.Application.ohMyJson
import app.termora.ApplicationScope
import app.termora.DeleteDataManager
import app.termora.account.AccountManager
import app.termora.database.Data
import app.termora.database.DataType
import app.termora.database.DatabaseManager
import app.termora.database.OwnerType
import org.slf4j.LoggerFactory

/**
 * 宏功能
 */
class MacroManager private constructor() {
    companion object {
        fun getInstance(): MacroManager {
            return ApplicationScope.forApplicationScope().getOrCreate(MacroManager::class) { MacroManager() }
        }

        private val log = LoggerFactory.getLogger(MacroManager::class.java)
    }

    private val database get() = DatabaseManager.getInstance()


    fun getMacros(): List<Macro> {
        return database.data<Macro>(DataType.Macro).sortedBy { it.created }
    }

    fun addMacro(macro: Macro) {

        val accountId = AccountManager.getInstance().getAccountId()

        database.save(
            Data(
                id = macro.id,
                ownerId = accountId,
                ownerType = OwnerType.User.name,
                type = DataType.Macro.name,
                data = ohMyJson.encodeToString(macro),
            )
        )

        if (log.isDebugEnabled) {
            log.debug("Added macro ${macro.id}")
        }
    }

    fun removeMacro(id: String) {
        database.delete(id, DataType.Macro.name)
        DeleteDataManager.getInstance().removeMacro(id)

        if (log.isDebugEnabled) {
            log.debug("Removed macro $id")
        }
    }
}