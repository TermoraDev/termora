package app.termora.highlight

import app.termora.DynamicColor
import app.termora.I18n
import app.termora.terminal.ColorPalette
import app.termora.terminal.TerminalColor
import com.formdev.flatlaf.ui.FlatLineBorder
import com.formdev.flatlaf.ui.FlatUIUtils
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Insets
import javax.swing.JPanel
import javax.swing.UIManager

class KeywordHighlightView(
    var arc: Int = UIManager.getInt("Component.arc"),
    var fontSize: Int = 0,
) : JPanel() {
    private val text = I18n.getString("termora.highlight.preview-text")

    var textColor: Color? = null
    var backgroundColor: Color? = null

    var bold = false
    var italic = false
    var lineThrough = false
    var underline = false


    init {
        border = FlatLineBorder(Insets(1, 1, 1, 1), DynamicColor.BorderColor, 1f, arc)
    }

    fun setKeywordHighlight(value: KeywordHighlight, colorPalette: ColorPalette) {
        textColor = if (value.textColor <= 16) {
            if (value.textColor == 0) {
                Color(colorPalette.getColor(TerminalColor.Basic.FOREGROUND))
            } else {
                Color(colorPalette.getXTerm256Color(value.textColor))
            }
        } else {
            Color(value.textColor)
        }

        backgroundColor = if (value.backgroundColor <= 16) {
            if (value.backgroundColor == 0) {
                Color(colorPalette.getColor(TerminalColor.Basic.BACKGROUND))
            } else {
                Color(colorPalette.getXTerm256Color(value.backgroundColor))
            }
        } else {
            Color(value.backgroundColor)
        }

        bold = value.bold
        italic = value.italic
        underline = value.underline
        lineThrough = value.lineThrough


    }

    override fun paintComponent(g: Graphics) {
        if (fontSize > 0) {
            g.font = g.font.deriveFont(fontSize * 1f)
        }

        if (bold && italic) {
            g.font = g.font.deriveFont(Font.ITALIC or Font.BOLD)
        } else if (bold) {
            g.font = g.font.deriveFont(Font.BOLD)
        } else if (italic) {
            g.font = g.font.deriveFont(Font.ITALIC)
        } else {
            g.font = g.font.deriveFont(Font.PLAIN)
        }

        g.color = backgroundColor
        g.fillRoundRect(0, 0, width, height, arc, arc)


        val fm = g.fontMetrics
        val fw = fm.stringWidth(text)
        val x = width / 2 - fw / 2
        val y = height / 2
        g.color = textColor
        FlatUIUtils.drawString(this, g, text, x, y + fm.height / 2 - fm.descent)

        if (underline) {
            g.drawLine(x, y + fm.height / 2, x + fw, y + fm.height / 2)
        }

        if (lineThrough) {
            g.drawLine(x, y, x + fw, y)
        }
    }
}
