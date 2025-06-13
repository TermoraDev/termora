package app.termora.terminal.panel

import app.termora.database.DatabaseManager
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class TerminalPanelMouseFloatingToolBarAdapter(
    private val terminalPanel: TerminalPanel,
    private val terminalDisplay: TerminalDisplay
) : MouseAdapter() {

    private val floatingToolbarEnable get() = DatabaseManager.getInstance().terminal.floatingToolbar

    override fun mouseMoved(e: MouseEvent) {
        if (!floatingToolbarEnable) {
            return
        }

        val floatingToolbar = terminalPanel.getData(FloatingToolbarPanel.FloatingToolbar) ?: return
        val width = terminalPanel.width
        val height = terminalPanel.height
        val widthDiff = (width * 0.25).toInt()
        val heightDiff = (height * 0.25).toInt()

        if (e.x in width - widthDiff..width && e.y in 0..heightDiff) {
            floatingToolbar.triggerShow()
        } else {
            floatingToolbar.triggerHide()
        }
    }

    override fun mouseExited(e: MouseEvent) {
        val floatingToolbar = terminalPanel.getData(FloatingToolbarPanel.FloatingToolbar) ?: return

        if (terminalDisplay.isShowing) {
            val rectangle = Rectangle(terminalDisplay.locationOnScreen, terminalDisplay.size)
            // 如果鼠标指针还在 terminalDisplay 中，那么就不需要隐藏
            if (rectangle.contains(e.locationOnScreen)) {
                return
            }
        }


        floatingToolbar.triggerHide()
    }


}