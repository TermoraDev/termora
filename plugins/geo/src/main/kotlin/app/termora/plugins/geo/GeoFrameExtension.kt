package app.termora.plugins.geo

import app.termora.EnableManager
import app.termora.FrameExtension
import app.termora.OptionPane
import app.termora.TermoraFrame
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

class GeoFrameExtension private constructor() : FrameExtension {
    companion object {
        val instance = GeoFrameExtension()

        private const val FIRST_KEY = "Plugins.Geo.isFirst"
    }

    private val enableManager get() = EnableManager.getInstance()


    override fun customize(frame: TermoraFrame) {
        // 已经加载完毕，那么不需要提示
        if (GeoApplicationRunnerExtension.instance.isReady()) return

        // 已经提示过了，直接退出
        val isFirst = enableManager.getFlag(FIRST_KEY, true)
        if (isFirst.not()) return

        frame.addWindowListener(object : WindowAdapter() {
            override fun windowOpened(e: WindowEvent) {
                enableManager.setFlag(FIRST_KEY, false)
                frame.removeWindowListener(this)
                SwingUtilities.invokeLater { showMessageDialog(frame) }
            }
        })
    }

    private fun showMessageDialog(window: Window) {
        OptionPane.showMessageDialog(
            window,
            GeoI18n.getString("termora.plugins.geo.first-message"),
            messageType = JOptionPane.INFORMATION_MESSAGE
        )
    }
}