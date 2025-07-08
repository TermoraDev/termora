package app.termora

import app.termora.Application.ohMyJson
import app.termora.actions.ActionManager
import app.termora.actions.MultipleAction
import app.termora.actions.SettingsAction
import app.termora.database.DatabaseManager
import app.termora.findeverywhere.FindEverywhereAction
import app.termora.snippet.SnippetAction
import org.apache.commons.lang3.StringUtils
import java.util.*
import javax.swing.event.EventListenerList

internal class TermoraToolbarModel private constructor() {
    companion object {
        fun getInstance(): TermoraToolbarModel {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(TermoraToolbarModel::class) { TermoraToolbarModel() }
        }
    }

    private val properties get() = DatabaseManager.getInstance().properties
    private val eventListener = EventListenerList()


    fun getActionManager() = ActionManager.getInstance()

    /**
     * 获取到所有的 Action
     */
    fun getAllActions(): List<ToolBarAction> {
        return listOf(
            ToolBarAction(SnippetAction.SNIPPET, true),
            ToolBarAction(Actions.SFTP, true),
            ToolBarAction(Actions.TERMINAL_LOGGER, true),
            ToolBarAction(Actions.MACRO, true),
            ToolBarAction(Actions.KEYWORD_HIGHLIGHT, true),
            ToolBarAction(Actions.KEY_MANAGER, true),
            ToolBarAction(MultipleAction.MULTIPLE, true),
            ToolBarAction(FindEverywhereAction.FIND_EVERYWHERE, true),
            ToolBarAction(SettingsAction.SETTING, true),
        )
    }

    /**
     * 获取到所有 Action，会根据用户个性化排序/显示
     */
    fun getActions(): List<ToolBarAction> {
        val text = properties.getString(
            "Termora.ToolBar.Actions",
            StringUtils.EMPTY
        )

        val actions = getAllActions()

        if (text.isBlank()) {
            return actions
        }

        // 存储的 action
        val storageActions = (ohMyJson.runCatching {
            ohMyJson.decodeFromString<List<ToolBarAction>>(text)
        }.getOrNull() ?: return actions).toMutableList()

        for (action in actions) {
            // 如果存储的 action 不包含这个，那么这个可能是新增的，新增的默认显示出来
            if (storageActions.none { it.id == action.id }) {
                storageActions.addFirst(ToolBarAction(action.id, true))
            }
        }

        // 如果存储的 Action 在所有 Action 里没有，那么移除
        storageActions.removeIf { e -> actions.none { e.id == it.id } }

        return storageActions
    }

    fun setActions(actions: List<ToolBarAction>) {
        assertEventDispatchThread()
        properties.putString("Termora.ToolBar.Actions", ohMyJson.encodeToString(actions))
        for (listener in eventListener.getListeners(TermoraToolbarModelListener::class.java)) {
            listener.onChanged()
        }
    }


    fun addTermoraToolbarModelListener(listener: TermoraToolbarModelListener): Disposable {
        eventListener.add(TermoraToolbarModelListener::class.java, listener)
        return object : Disposable {
            override fun dispose() {
                removeTermoraToolbarModelListener(listener)
            }
        }
    }

    fun removeTermoraToolbarModelListener(listener: TermoraToolbarModelListener) {
        eventListener.remove(TermoraToolbarModelListener::class.java, listener)
    }

    interface TermoraToolbarModelListener : EventListener {
        fun onChanged()
    }
}