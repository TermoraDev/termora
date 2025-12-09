package app.termora

interface TerminalTabbedManager {
    fun addTerminalTab(tab: TerminalTab, selected: Boolean = true)
    fun addTerminalTab(index: Int, tab: TerminalTab, selected: Boolean = true)
    fun getSelectedTerminalTab(): TerminalTab?
    fun getTerminalTabs(): List<TerminalTab>
    fun setSelectedTerminalTab(tab: TerminalTab)
    fun closeTerminalTab(tab: TerminalTab, disposable: Boolean = true, reconnect: Boolean = false)
    fun refreshTerminalTabs()
    fun indexOfTerminalTab(tab: TerminalTab): Int
}