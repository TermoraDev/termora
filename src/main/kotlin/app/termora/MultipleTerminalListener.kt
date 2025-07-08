package app.termora


import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import app.termora.actions.MultipleAction
import app.termora.terminal.Terminal
import app.termora.terminal.TerminalColor
import app.termora.terminal.TextStyle
import app.termora.terminal.panel.FloatingToolbarPanel
import app.termora.terminal.panel.TerminalDisplay
import app.termora.terminal.panel.TerminalPaintListener
import app.termora.terminal.panel.TerminalPanel
import org.apache.commons.lang3.StringUtils
import java.awt.Color
import java.awt.Graphics
import java.util.*

class MultipleTerminalListener : TerminalPaintListener {
    override fun after(
        offset: Int,
        count: Int,
        g: Graphics,
        terminalPanel: TerminalPanel,
        terminalDisplay: TerminalDisplay,
        terminal: Terminal
    ) {
        val windowScope = AnActionEvent(terminalPanel, StringUtils.EMPTY, EventObject(terminalPanel))
            .getData(DataProviders.WindowScope) ?: return
        if (MultipleAction.getInstance().isSelected(windowScope).not()) return

        val oldFont = g.font
        val colorPalette = terminal.getTerminalModel().getColorPalette()
        val text = I18n.getString("termora.tools.multiple")
        val font = terminalDisplay.getDisplayFont(text, TextStyle.Default)
        val width = g.getFontMetrics(font).stringWidth(text)
        // 正在搜索那么需要下移
        val finding = terminal.getTerminalModel().getData(TerminalPanel.Finding, false)

        // 如果悬浮窗正在显示，那么需要下移
        val floatingToolBar = terminalPanel.getData(FloatingToolbarPanel.FloatingToolbar)?.isVisible == true

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