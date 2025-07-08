package app.termora.keymap

import app.termora.ApplicationScope
import app.termora.Disposable
import app.termora.account.AccountManager
import app.termora.database.Data
import app.termora.database.DataType
import app.termora.database.DatabaseManager
import app.termora.database.OwnerType
import com.formdev.flatlaf.util.SystemInfo
import org.slf4j.LoggerFactory

class KeymapManager private constructor() : Disposable {

    companion object {
        private val log = LoggerFactory.getLogger(KeymapManager::class.java)

        fun getInstance(): KeymapManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(KeymapManager::class) { KeymapManager() }
        }
    }

    private val database get() = DatabaseManager.getInstance()
    private val properties get() = DatabaseManager.getInstance().properties
    private val keymaps = linkedMapOf<String, Keymap>()
    private val accountManager get() = AccountManager.getInstance()
    private val activeKeymap get() = properties.getString("Keymap.Active")

    init {
        try {
            for (data in database.rawData(DataType.Keymap)) {
                try {
                    val keymap = Keymap.fromJSON(data.data) ?: continue
                    keymaps[keymap.name] = keymap
                } catch (e: Exception) {
                    if (log.isWarnEnabled) {
                        log.warn(e.message, e)
                    }
                }
            }

        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
        }

        MacOSKeymap.getInstance().let { keymaps[it.name] = it }
        WindowsKeymap.getInstance().let { keymaps[it.name] = it }

    }


    fun getActiveKeymap(): Keymap {
        val name = activeKeymap
        if (name != null) {
            val keymap = getKeymap(name)
            if (keymap != null) {
                return keymap
            }
        }

        return if (SystemInfo.isMacOS) {
            MacOSKeymap.getInstance()
        } else {
            WindowsKeymap.getInstance()
        }
    }

    fun getKeymap(name: String): Keymap? {
        return keymaps[name]
    }

    fun getKeymaps(): List<Keymap> {
        return keymaps.values.toList()
    }

    fun addKeymap(keymap: Keymap) {
        keymaps.putFirst(keymap.name, keymap)
        val accountId = accountManager.getAccountId()

        database.saveAndIncrementVersion(
            Data(
                id = keymap.id,
                ownerId = accountId,
                ownerType = OwnerType.User.name,
                type = DataType.Keymap.name,
                data = keymap.toJSON(),
            )
        )
    }

    fun removeKeymap(id: String) {
        for (name in keymaps.keys.toTypedArray()) {
            if (keymaps.getValue(name).id == id) {
                keymaps.remove(name)
            }
        }
        database.delete(id, DataType.Keymap.name)
    }

}