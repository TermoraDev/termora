package app.termora.tree

import java.awt.Color
import java.awt.Graphics2D
import javax.swing.JComponent

open class MarkerSimpleTreeCellAnnotation(
    val text: String,
    /**
     * 原来的大小 -N
     */
    protected var fontSize: Float = 2f,
    protected var foreground: Color? = null,
    protected var background: Color? = null,
) : SimpleTreeCellAnnotation {

    override fun getWidth(c: JComponent): Int {
        return stringWidth(c) + if (background == null) 0 else 4
    }

    private fun stringWidth(c: JComponent): Int {
        return c.getFontMetrics(c.font).stringWidth(text)
    }

    override fun paint(c: JComponent, g: Graphics2D) {
        val width = getWidth(c)

        if (background != null) {
            g.color = background
            g.fillRoundRect(0, 4, width, c.height - 8, 4, 4)
        }

        g.color = foreground ?: g.color
        g.font = g.font.deriveFont(g.font.size2D - fontSize)
        val fm = c.getFontMetrics(g.font)

        g.drawString(text, (width - fm.stringWidth(text)) / 2, (c.height - fm.height) / 2 + fm.ascent)

    }

}