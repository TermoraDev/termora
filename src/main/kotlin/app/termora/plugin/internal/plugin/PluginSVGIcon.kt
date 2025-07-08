package app.termora.plugin.internal.plugin

import app.termora.plugin.PluginDescriptor
import app.termora.restore
import app.termora.save
import app.termora.setupAntialiasing
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.util.UIScale
import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.parser.SVGLoader
import com.github.weisj.jsvg.parser.impl.MutableLoaderContext
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.io.InputStream
import javax.swing.Icon

class PluginSVGIcon(input: InputStream, dark: InputStream? = null) : Icon {

    companion object {
        private val svgLoader = SVGLoader()

    }

    private val document = svgLoader.load(input, null, MutableLoaderContext.createDefault())
    private val darkDocument = dark?.let { svgLoader.load(it, null, MutableLoaderContext.createDefault()) }

    override fun getIconHeight(): Int {
        return 32
    }

    override fun getIconWidth(): Int {
        return 32
    }

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        if (g is Graphics2D) {
            runCatching {
                if (FlatLaf.isLafDark() && darkDocument != null) {
                    paint(darkDocument, g, x, y)
                } else if (document != null) {
                    paint(document, g, x, y)
                } else {
                    PluginDescriptor.defaultIcon.paintIcon(c, g, x, y)
                }
            }
        }
    }

    private fun paint(document: SVGDocument, g: Graphics2D, x: Int, y: Int) {
        g.save()
        setupAntialiasing(g)

        g.translate(x, y)
        g.clipRect(0, 0, iconWidth, iconHeight)
        UIScale.scaleGraphics(g)

        if (iconWidth > 0 || iconHeight > 0) {
            val svgSize = document.size()
            val sx = (if (iconWidth > 0) iconWidth / svgSize.width else 1f).toDouble()
            val sy = (if (iconHeight > 0) iconHeight / svgSize.height else 1f).toDouble()
            if (sx != 1.0 || sy != 1.0) g.scale(sx, sy)
        }

        document.render(null, g)

        g.restore()
    }
}