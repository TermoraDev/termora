package app.termora.keymap

import app.termora.ApplicationScope
import app.termora.DialogWrapper
import app.termora.Disposable
import app.termora.SwingUtils
import app.termora.account.AccountManager
import app.termora.actions.AnActionEvent
import app.termora.database.Data
import app.termora.database.DataType
import app.termora.database.DatabaseManager
import app.termora.database.OwnerType
import com.formdev.flatlaf.util.SystemInfo
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.action.ActionManager
import org.slf4j.LoggerFactory
import java.awt.Container
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JPopupMenu
import javax.swing.KeyStroke

class KeymapManager private constructor() : Disposable {

    companion object {
        private val log = LoggerFactory.getLogger(KeymapManager::class.java)

        fun getInstance(): KeymapManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(KeymapManager::class) { KeymapManager() }
        }
    }

    private val keymapKeyEventDispatcher = KeymapKeyEventDispatcher()
    private val database get() = DatabaseManager.getInstance()
    private val properties get() = DatabaseManager.getInstance().properties
    private val keymaps = linkedMapOf<String, Keymap>()
    private val accountManager get() = AccountManager.getInstance()
    private val activeKeymap get() = properties.getString("Keymap.Active")
    private val keyboardFocusManager by lazy { KeyboardFocusManager.getCurrentKeyboardFocusManager() }

    init {
        keyboardFocusManager.addKeyEventDispatcher(keymapKeyEventDispatcher)

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

        MacOSKeymap.getInstance().let {
            keymaps[it.name] = it
        }

        WindowsKeymap.getInstance().let {
            keymaps[it.name] = it
        }

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

        database.save(
            Data(
                id = keymap.id,
                ownerId = accountId,
                ownerType = OwnerType.User.name,
                type = DataType.KeywordHighlight.name,
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

    private inner class KeymapKeyEventDispatcher : KeyEventDispatcher {

        override fun dispatchKeyEvent(e: KeyEvent): Boolean {
            if (e.isConsumed || e.id != KeyEvent.KEY_PRESSED || e.modifiersEx == 0) {
                return false
            }

            val keyStroke = KeyStroke.getKeyStrokeForEvent(e)
            val component = e.source

            if (component is JComponent) {
                // 如果这个键已经被组件注册了，那么忽略
                if (component.getConditionForKeyStroke(keyStroke) != JComponent.UNDEFINED_CONDITION) {
                    return false
                }
            }


            val shortcuts = getActiveKeymap()
            val actionIds = shortcuts.getActionIds(KeyShortcut(keyStroke))
            if (actionIds.isEmpty()) {
                return false
            }

            val focusedWindow = keyboardFocusManager.focusedWindow
            if (focusedWindow is DialogWrapper) {
                if (!focusedWindow.processGlobalKeymap) {
                    return false
                }
            } else if (focusedWindow is JDialog) {
                return false
            }

            // 如果当前有 Popup ，那么不派发事件
            val c = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            if (c is Container) {
                val popups: List<JPopupMenu> = SwingUtils.getDescendantsOfType(
                    JPopupMenu::class.java,
                    c, true
                )
                if (popups.isNotEmpty()) {
                    return false
                }
            }

            val evt = AnActionEvent(e.source, StringUtils.EMPTY, e)
            for (actionId in actionIds) {
                val action = ActionManager.getInstance().getAction(actionId) ?: continue
                if (!action.isEnabled) {
                    continue
                }
                action.actionPerformed(evt)
                if (evt.isConsumed) {
                    return true
                }
            }

            return false
        }

    }

    override fun dispose() {
        keyboardFocusManager.removeKeyEventDispatcher(keymapKeyEventDispatcher)
    }
}