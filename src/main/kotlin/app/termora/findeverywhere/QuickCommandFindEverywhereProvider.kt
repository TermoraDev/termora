package app.termora.findeverywhere

import app.termora.Actions
import app.termora.I18n
import app.termora.Icons
import app.termora.Scope
import app.termora.actions.NewHostAction
import app.termora.actions.OpenLocalTerminalAction
import app.termora.actions.QuickConnectAction
import app.termora.snippet.SnippetAction
import com.formdev.flatlaf.FlatLaf
import org.jdesktop.swingx.action.ActionManager
import javax.swing.Icon

class QuickCommandFindEverywhereProvider : FindEverywhereProvider {
    private val actionManager get() = ActionManager.getInstance()

    override fun find(pattern: String, scope: Scope): List<FindEverywhereResult> {
        val list = mutableListOf<FindEverywhereResult>()
        actionManager.let { list.add(CreateHostFindEverywhereResult()) }

        // Local terminal
        actionManager.getAction(OpenLocalTerminalAction.LOCAL_TERMINAL)?.let { list.add(ActionFindEverywhereResult(it)) }
        // Snippet
        actionManager.getAction(SnippetAction.SNIPPET)?.let { list.add(ActionFindEverywhereResult(it)) }
        // SFTP
        actionManager.getAction(Actions.SFTP)?.let { list.add(ActionFindEverywhereResult(it)) }
        // quick connect
        actionManager.getAction(QuickConnectAction.QUICK_CONNECT)?.let { list.add(ActionFindEverywhereResult(it)) }

        return list
    }


    override fun order(): Int {
        return Int.MIN_VALUE
    }

    override fun group(): String {
        return I18n.getString("termora.find-everywhere.groups.quick-actions")
    }

    private class CreateHostFindEverywhereResult : ActionFindEverywhereResult(
        ActionManager.getInstance().getAction(NewHostAction.NEW_HOST)
    ) {
        override fun getIcon(isSelected: Boolean): Icon {
            if (isSelected) {
                if (!FlatLaf.isLafDark()) {
                    return Icons.openNewTab.dark
                }
            }
            return Icons.openNewTab
        }


        override fun toString(): String {
            return I18n.getString("termora.new-host.title")
        }
    }


}