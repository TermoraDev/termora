package app.termora

import app.termora.terminal.Terminal
import app.termora.terminal.TerminalColor
import app.termora.terminal.TextStyle
import app.termora.terminal.panel.TerminalDisplay
import app.termora.terminal.panel.TerminalFloatingToolBar
import app.termora.terminal.panel.TerminalPaintListener
import app.termora.terminal.panel.TerminalPanel
import org.jdesktop.swingx.action.ActionManager
import java.awt.Color
import java.awt.Graphics

class MultipleTerminalListener : TerminalPaintListener {
    override fun after(
        offset: Int,
        count: Int,
        g: Graphics,
        terminalPanel: TerminalPanel,
        terminalDisplay: TerminalDisplay,
        terminal: Terminal
    ) {
        if (!ActionManager.getInstance().isSelected(Actions.MULTIPLE)) {
            return
        }

        val oldFont = g.font
        val colorPalette = terminal.getTerminalModel().getColorPalette()
        val text = I18n.getString("termora.tools.multiple")
        val font = terminalDisplay.getDisplayFont(text, TextStyle.Default)
        val width = g.getFontMetrics(font).stringWidth(text)

        // 正在搜索那么需要下移
        val finding = terminal.getTerminalModel().getData(TerminalPanel.Finding, false)

        // 如果悬浮窗正在显示，那么需要下移
        val floatingToolBar =
            if (terminal.getTerminalModel().hasData(TerminalFloatingToolBar.StateSupport)) {
                terminal.getTerminalModel().getData(TerminalFloatingToolBar.StateSupport)
                    .get() == TerminalFloatingToolBar.Companion.State.Shown
            } else {
                false
            }

        var y = g.fontMetrics.ascent
        if (finding) {
            y += g.fontMetrics.height + g.fontMetrics.ascent / 2
        }

        if (floatingToolBar) {
            y += g.fontMetrics.height + g.fontMetrics.ascent / 2
        }

        g.font = font
        g.color = Color(colorPalette.getColor(TerminalColor.Normal.RED))
        g.drawString(
            text,
            terminalDisplay.width - width - terminalPanel.getAverageCharWidth() / 2,
            y
        )
        g.font = oldFont
    }
}