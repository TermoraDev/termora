package app.termora.terminal.panel

import app.termora.Application
import app.termora.ApplicationScope
import app.termora.database.DatabaseManager
import app.termora.terminal.*
import java.awt.Graphics
import java.net.URI
import kotlin.math.min

class TerminalHyperlinkPaintListener private constructor() : TerminalPaintListener {
    companion object {
        fun getInstance(): TerminalHyperlinkPaintListener {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(TerminalHyperlinkPaintListener::class) { TerminalHyperlinkPaintListener() }
        }
    }

    private val regex = Regex("https?://\\S*[^.\\s'\",()<>\\[\\]]")
    private val isEnableHyperlink get() = DatabaseManager.getInstance().terminal.hyperlink

    override fun before(
        offset: Int,
        count: Int,
        g: Graphics,
        terminalPanel: TerminalPanel,
        terminalDisplay: TerminalDisplay,
        terminal: Terminal
    ) {

        if (isEnableHyperlink.not()) return

        val document = terminal.getDocument()
        var startOffset = offset
        var endOffset = startOffset + count

        // 获取开始行
        while (startOffset > 0) {
            if (document.getLine(startOffset).wrapped) {
                startOffset--
            } else {
                break
            }
        }

        // 获取结束行
        while (endOffset < document.getLineCount()) {
            if (document.getLine(endOffset).wrapped) {
                endOffset++
            } else {
                break
            }
        }

        // 删除之前的
        terminal.getMarkupModel().removeAllHighlighters(Highlighter.HYPERLINK)

        val rows = min(terminal.getDocument().getLineCount(), offset + count)
        val list = mutableListOf<Triple<Char, Int, Int>>()

        for (i in startOffset until endOffset) {
            val line = terminal.getDocument().getLine(i + 1)
            val entry = line.chars()
            for (j in entry.indices) {
                val (c, _) = entry[j]
                if (c == Char.SoftHyphen) {
                    continue
                } else if (c == Char.Null) {
                    break
                }
                list.add(Triple(c, i + 1, j + 1))
            }

            if (line.wrapped && i + 1 < rows) {
                continue
            }

            val text = list.map { it.first }.joinToString("")
            for (e in regex.findAll(text).map { Triple(it.range.first, it.value, it.range.last) }) {
                terminal.getMarkupModel().addHighlighter(
                    HyperlinkHighlighter(
                        HighlighterRange(
                            Position(list[e.first].second, list[e.first].third),
                            Position(list[e.third].second, list[e.third].third)
                        ),
                        terminal,
                        e.second
                    ) { _, url ->
                        Application.browse(URI.create(url))
                    }
                )

            }

            list.clear()
        }
    }

    override fun after(
        offset: Int,
        count: Int,
        g: Graphics,
        terminalPanel: TerminalPanel,
        terminalDisplay: TerminalDisplay,
        terminal: Terminal
    ) {
        if (isEnableHyperlink.not()) {
            // 删除之前的
            terminal.getMarkupModel().removeAllHighlighters(Highlighter.HYPERLINK)
        }
    }

}