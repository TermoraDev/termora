package app.termora

import app.termora.plugin.Extension
import java.awt.Window
import javax.swing.JComponent

interface GlassPaneAwareExtension : Extension {
    fun setGlassPane(window: Window, glassPane: JComponent)
}