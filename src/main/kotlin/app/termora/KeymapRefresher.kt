package app.termora

import app.termora.database.DatabaseChangedExtension
import app.termora.database.DatabasePropertiesChangedExtension
import app.termora.keymap.KeymapManager

internal class KeymapRefresher private constructor() : DatabasePropertiesChangedExtension, DatabaseChangedExtension {
    companion object {
        fun getInstance(): KeymapRefresher {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(KeymapRefresher::class) { KeymapRefresher() }
        }
    }

    private val listeners = mutableListOf<() -> Unit>()
    private var currentKeymap: String? = null
    private val keymapManager get() = KeymapManager.getInstance()
    private val activeKeymapName get() = keymapManager.getActiveKeymap().name

    override fun onDataChanged(
        id: String,
        type: String,
        action: DatabaseChangedExtension.Action,
        source: DatabaseChangedExtension.Source
    ) {
        if (type != "Keymap") return
        refresh(true)
    }

    override fun onPropertyChanged(name: String, key: String, value: String) {
        if (name != "Setting.Properties") return
        if (key != "Keymap.Active") return
        refresh()
    }

    private fun refresh(force: Boolean = false) {
        synchronized(this) {
            if (force.not()) {
                if (currentKeymap == activeKeymapName) {
                    return
                }
            }

            currentKeymap = activeKeymapName

            for (function in listeners) {
                function.invoke()
            }
        }
    }

    fun addRefreshListener(listener: () -> Unit): Disposable {
        synchronized(this) {
            listeners.add(listener)
            return object : Disposable {
                override fun dispose() {
                    removeRefreshListener(listener)
                }
            }
        }
    }

    fun removeRefreshListener(listener: () -> Unit) {
        synchronized(this) { listeners.remove(listener) }
    }

}