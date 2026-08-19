package app.termora

import com.formdev.flatlaf.FlatLaf
import org.apache.commons.lang3.RandomUtils
import java.awt.*
import javax.swing.JComponent
import javax.swing.UIManager

class BannerPanel(fontSize: Int = 11, val beautiful: Boolean = false) : JComponent() {
    private val banner = """
  ______                                    
 /_  __/__  _________ ___  ____  _________ _
  / / / _ \/ ___/ __ `__ \/ __ \/ ___/ __ `/
 / / /  __/ /  / / / / / / /_/ / /  / /_/ / 
/_/  \___/_/  /_/ /_/ /_/\____/_/   \__,_/  
""".trimIndent().lines()

    private val colors = mutableListOf<Color>()

    init {
        font = Font(Font.MONOSPACED, Font.PLAIN, fontSize)
        val fontMetrics = getFontMetrics(font)
        preferredSize = Dimension(banner.maxOf(fontMetrics::stringWidth), fontMetrics.height * banner.size)
        size = preferredSize
    }

    public override fun paintComponent(g: Graphics) {
        if (g is Graphics2D) {
            g.setRenderingHints(
                RenderingHints(
                    RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON
                )
            )
        }

        g.font = font
        g.color = foreground ?: UIManager.getColor("TextField.placeholderForeground")

        val height = g.fontMetrics.height
        val descent = g.fontMetrics.descent
        val offset = width / 2 - g.fontMetrics.stringWidth(banner.maxBy { it.length }) / 2
        val insecure = RandomUtils.insecure()
        var index = 0

        for (i in banner.indices) {
            var x = offset
            val y = height * (i + 1) - descent
            val chars = banner[i].toCharArray()
            for (j in chars.indices) {
                if (beautiful) {
                    if (colors.size <= index) {
                        colors.add(
                            Color(
                                insecure.randomInt(0, 255),
                                insecure.randomInt(0, 255),
                                insecure.randomInt(0, 255)
                            )
                        )
                    }
                    val color = colors[index++]
                    g.color = if (FlatLaf.isLafDark()) color.brighter() else color.darker()
                }
                g.drawChars(chars, j, 1, x, y)
                x += g.fontMetrics.charWidth(chars[j])
            }
        }

    }
}
