package app.termora.actions

import app.termora.WindowScope

interface StateAction {
    fun isSelected(windowScope: WindowScope): Boolean
    fun setSelected(windowScope: WindowScope, selected: Boolean)
}