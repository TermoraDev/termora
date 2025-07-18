package app.termora.plugins.bg

import app.termora.GlassPaneExtension
import app.termora.WindowScope
import com.formdev.flatlaf.FlatLaf
import java.awt.AlphaComposite
import java.awt.Graphics2D
import javax.swing.JComponent

class BGGlassPaneExtension private constructor() : GlassPaneExtension {
    companion object {
        val instance = BGGlassPaneExtension()
    }

    override fun paint(scope: WindowScope, c: JComponent, g2d: Graphics2D) {

        val img = BackgroundManager.getInstance().getBackgroundImage() ?: return
        g2d.composite = AlphaComposite.getInstance(
            AlphaComposite.SRC_OVER,
            if (FlatLaf.isLafDark()) 0.2f else 0.1f
        )
        g2d.drawImage(img, 0, 0, c.width, c.height, null)
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER)

    }
}