package app.termora.snippet

import java.awt.*
import javax.swing.JComponent
import javax.swing.UIManager

class SnippetBannerPanel(fontSize: Int = 12) : JComponent() {
    private val banner = """
   _____       _                  __ 
  / ___/____  (_)___  ____  ___  / /_
  \__ \/ __ \/ / __ \/ __ \/ _ \/ __/
 ___/ / / / / / /_/ / /_/ /  __/ /_  
/____/_/ /_/_/ .___/ .___/\___/\__/  
            /_/   /_/                
""".trimIndent().lines()

    init {
        font = Font(Font.MONOSPACED, Font.PLAIN, fontSize)
        val fontMetrics = getFontMetrics(font)
        preferredSize = Dimension(banner.maxOf(fontMetrics::stringWidth), fontMetrics.height * banner.size)
        size = preferredSize
    }

    override fun paintComponent(g: Graphics) {
        if (g is Graphics2D) {
            g.setRenderingHints(
                RenderingHints(
                    RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON
                )
            )
        }

        g.font = font
        g.color = UIManager.getColor("TextField.placeholderForeground")

        val height = g.fontMetrics.height
        val descent = g.fontMetrics.descent
        val offset = width / 2 - g.fontMetrics.stringWidth(banner.maxBy { it.length }) / 2

        for (i in banner.indices) {
            var x = offset
            val y = height * (i + 1) - descent
            val chars = banner[i].toCharArray()
            for (j in chars.indices) {
                g.drawChars(chars, j, 1, x, y)
                x += g.fontMetrics.charWidth(chars[j])
            }
        }

    }
}
