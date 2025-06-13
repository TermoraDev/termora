package app.termora.tree

import app.termora.plugin.Extension
import javax.swing.JCheckBoxMenuItem
import javax.swing.JTree

interface HostTreeShowMoreEnableExtension : Extension {
    fun createJCheckBoxMenuItem(tree: JTree): JCheckBoxMenuItem
}