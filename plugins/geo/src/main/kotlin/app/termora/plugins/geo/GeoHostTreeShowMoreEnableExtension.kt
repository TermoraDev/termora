package app.termora.plugins.geo

import app.termora.EnableManager
import app.termora.I18n
import app.termora.SwingUtils
import app.termora.TermoraFrameManager
import app.termora.tree.HostTreeShowMoreEnableExtension
import app.termora.tree.NewHostTree
import javax.swing.JCheckBoxMenuItem
import javax.swing.JTree
import javax.swing.SwingUtilities

internal class GeoHostTreeShowMoreEnableExtension private constructor() : HostTreeShowMoreEnableExtension {
    companion object {
        private const val KEY = "Plugins.Geo.ShowMore.Enable"

        val instance = GeoHostTreeShowMoreEnableExtension()
    }

    private val enableManager get() = EnableManager.getInstance()

    override fun createJCheckBoxMenuItem(tree: JTree): JCheckBoxMenuItem {
        val item = JCheckBoxMenuItem("Geo")
        item.isEnabled = GeoApplicationRunnerExtension.instance.isReady()
        item.isSelected = item.isEnabled && enableManager.getFlag(KEY, true)
        if (item.isEnabled.not()) {
            item.text = GeoI18n.getString("termora.plugins.geo.coming-soon")
        }
        item.addActionListener {
            enableManager.setFlag(KEY, item.isSelected)
            updateComponentTreeUI()
        }
        return item
    }

    fun updateComponentTreeUI() {
        // reload all tree
        for (frame in TermoraFrameManager.getInstance().getWindows()) {
            for (tree in SwingUtils.getDescendantsOfClass(NewHostTree::class.java, frame)) {
                SwingUtilities.updateComponentTreeUI(tree)
            }
        }
    }

    fun isShowMore(): Boolean {
        return enableManager.getFlag(KEY, true)
    }
}