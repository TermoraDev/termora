package app.termora.terminal.panel

import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class TerminalPanelMouseFloatingToolBarAdapter(
    private val floatingToolBar: TerminalFloatingToolBar,
    private val terminalPanel: TerminalPanel,
    private val terminalDisplay: TerminalDisplay
) : MouseAdapter() {

    override fun mouseMoved(e: MouseEvent) {
        if (!canShowFloatingToolBar()) {
            return
        }

        val width = terminalPanel.width
        val height = terminalPanel.height
        val widthDiff = (width * 0.25).toInt()
        val heightDiff = (height * 0.25).toInt()

        if (e.x in width - widthDiff..width && e.y in 0..heightDiff) {
            if (floatingToolBar.state == TerminalFloatingToolBar.Companion.State.Shown) {
                return
            }
            floatingToolBar.show()
        } else {
            if (floatingToolBar.state == TerminalFloatingToolBar.Companion.State.Hidden) {
                return
            }
            floatingToolBar.hide()
        }
    }

    override fun mouseExited(e: MouseEvent) {
        if (!canShowFloatingToolBar()) {
            return
        }

        if (terminalDisplay.isShowing) {
            val rectangle = Rectangle(terminalDisplay.locationOnScreen, terminalDisplay.size)
            // 如果鼠标指针还在 terminalDisplay 中，那么就不需要隐藏
            if (rectangle.contains(e.locationOnScreen)) {
                return
            }
        }


        floatingToolBar.hide()
    }

    private fun canShowFloatingToolBar(): Boolean {
        return floatingToolBar.actionCount >= 1 && floatingToolBar.state != TerminalFloatingToolBar.Companion.State.Removed
    }

}