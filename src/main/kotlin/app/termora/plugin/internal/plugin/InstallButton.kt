package app.termora.plugin.internal.plugin

import app.termora.DynamicColor
import com.formdev.flatlaf.extras.components.FlatButton
import com.formdev.flatlaf.ui.FlatButtonUI
import java.awt.Color
import java.awt.Graphics
import javax.swing.JComponent
import kotlin.math.round

open class InstallButton : FlatButton() {
    var progress: Int = 0
    var installing = false
        set(value) {
            field = value
            repaint()
        }

    private var paintingBackground = false


    init {
        setUI(InstallButtonUI())
    }

    override fun updateUI() {
        setUI(InstallButtonUI())
        super.updateUI()
    }


    override fun getWidth(): Int {
        val width = super.getWidth()
        if (installing && paintingBackground) {
            return round(width * (progress / 100.0)).toInt()
        }
        return width
    }

    override fun getText(): String? {
        if (installing) {
            return "${progress}%"
        }
        return super.getText()
    }


    private inner class InstallButtonUI : FlatButtonUI(true) {
        override fun paintBackground(g: Graphics?, c: JComponent?) {
            if (installing) {
                paintingBackground = true
                super.paintBackground(g, c)
                paintingBackground = false
            } else {
                super.paintBackground(g, c)
            }
        }

        override fun getBackground(c: JComponent): Color? {
            if (installing && paintingBackground && c.width > 0) {
                return DynamicColor.BorderColor
            }
            return super.getBackground(c)
        }
    }
}