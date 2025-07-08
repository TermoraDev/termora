package app.termora.findeverywhere

import app.termora.Actions
import app.termora.I18n
import app.termora.Scope
import app.termora.WindowScope
import app.termora.actions.MultipleAction

import org.jdesktop.swingx.action.ActionManager

class QuickActionsFindEverywhereProvider(private val windowScope: WindowScope) : FindEverywhereProvider {
    private val actions = listOf(
        Actions.KEY_MANAGER,
        Actions.KEYWORD_HIGHLIGHT,
        MultipleAction.MULTIPLE,
    )

    override fun find(pattern: String, scope: Scope): List<FindEverywhereResult> {
        if (scope != windowScope) return emptyList()
        val actionManager = ActionManager.getInstance()
        val results = ArrayList<FindEverywhereResult>()
        for (action in actions) {
            val ac = actionManager.getAction(action)
            if (ac == null) {
                continue
            } else {
                results.add(ActionFindEverywhereResult(ac))
            }
        }
        return results
    }


    override fun order(): Int {
        return Integer.MIN_VALUE + 3
    }

    override fun group(): String {

        return I18n.getString("termora.find-everywhere.groups.tools")
    }


}