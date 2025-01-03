package app.termora.highlight

import app.termora.terminal.*
import app.termora.terminal.panel.TerminalDisplay
import app.termora.terminal.panel.TerminalPaintListener
import app.termora.terminal.panel.TerminalPanel
import java.awt.Graphics
import kotlin.math.min
import kotlin.random.Random

class KeywordHighlightPaintListener private constructor() : TerminalPaintListener {

    companion object {
        val instance by lazy { KeywordHighlightPaintListener() }
        private val tag = Random.nextInt()
    }

    private val keywordHighlightManager by lazy { KeywordHighlightManager.instance }

    override fun before(
        offset: Int,
        count: Int,
        g: Graphics,
        terminalPanel: TerminalPanel,
        terminalDisplay: TerminalDisplay,
        terminal: Terminal
    ) {
        for (highlight in keywordHighlightManager.getKeywordHighlights()) {
            if (!highlight.enabled) {
                continue
            }

            val document = terminal.getDocument()
            val kinds = SubstrFinder(object : Iterator<TerminalLine> {
                private var index = offset + 1
                private val maxCount = min(index + count, document.getLineCount())
                override fun hasNext(): Boolean {
                    return index <= maxCount
                }

                override fun next(): TerminalLine {
                    return document.getLine(index++)
                }

            }, CharArraySubstr(highlight.keyword.toCharArray())).find(!highlight.matchCase)

            for (kind in kinds) {
                terminal.getMarkupModel().addHighlighter(
                    KeywordHighlightHighlighter(
                        HighlighterRange(
                            kind.startPosition.copy(y = kind.startPosition.y + offset),
                            kind.endPosition.copy(y = kind.endPosition.y + offset)
                        ),
                        terminal = terminal,
                        keywordHighlight = highlight,
                    )
                )
            }

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
        terminal.getMarkupModel().removeAllHighlighters(tag)
    }


    private class KeywordHighlightHighlighter(
        range: HighlighterRange, terminal: Terminal,
        val keywordHighlight: KeywordHighlight
    ) : TagHighlighter(range, terminal, tag) {
        override fun getTextStyle(position: Position, textStyle: TextStyle): TextStyle {
            return textStyle.copy(
                foreground = keywordHighlight.textColor,
                background = keywordHighlight.backgroundColor,
                bold = keywordHighlight.bold,
                italic = keywordHighlight.italic,
                underline = keywordHighlight.underline,
                lineThrough = keywordHighlight.lineThrough,
            )
        }
    }
}