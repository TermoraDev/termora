package app.termora

import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.Icon

class ColorIcon(
    private val width: Int = 16,
    private val height: Int = 16,
    private val color: Color,
    private val circle: Boolean = true
) : Icon {
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        if (g is Graphics2D) {
            g.save()
            setupAntialiasing(g)
            g.color = color
            if (circle) {
                g.fillRoundRect(x, y, width, width, width, width)
            } else {
                g.fillRect(x, y, iconWidth, iconHeight)
            }
            g.restore()
        }
    }

    override fun getIconWidth(): Int {
        return width
    }

    override fun getIconHeight(): Int {
        return height
    }
}