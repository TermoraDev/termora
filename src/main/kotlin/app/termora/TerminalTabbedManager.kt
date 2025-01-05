package app.termora

interface TerminalTabbedManager {
    fun addTerminalTab(tab: TerminalTab)
    fun getSelectedTerminalTab(): TerminalTab?
    fun getTerminalTabs(): List<TerminalTab>
    fun setSelectedTerminalTab(tab: TerminalTab)
}