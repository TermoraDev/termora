package app.termora.actions

import app.termora.*

class MultipleAction private constructor() : AnAction(
    I18n.getString("termora.tools.multiple"),
    Icons.vcs
), StateAction {

    companion object {

        /**
         * 将命令发送到多个会话
         */
        const val MULTIPLE = "MultipleAction"

        fun getInstance(): MultipleAction {
            return ApplicationScope.forApplicationScope().getOrCreate(MultipleAction::class) { MultipleAction() }
        }
    }

    init {
        setStateAction()
    }

    override fun actionPerformed(evt: AnActionEvent) {
        super.setSelected(false)
        val windowScope = evt.getData(DataProviders.WindowScope) ?: return
        setSelected(windowScope, isSelected(windowScope).not())
        TerminalPanelFactory.getInstance().repaintAll()
    }

    override fun isSelected(): Boolean {
        throw UnsupportedOperationException()
    }

    override fun isSelected(windowScope: WindowScope): Boolean {
        return windowScope.getBoolean("MultipleAction.isSelected", false)
    }

    override fun setSelected(windowScope: WindowScope, selected: Boolean) {
        windowScope.putBoolean("MultipleAction.isSelected", selected)
        putValue("MultipleAction.isSelected", selected)
    }


}