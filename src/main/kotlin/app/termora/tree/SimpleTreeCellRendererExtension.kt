package app.termora.tree

import app.termora.plugin.Extension
import javax.swing.JTree

interface SimpleTreeCellRendererExtension : Extension {
    fun createAnnotations(
        tree: JTree,
        value: Any?,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): List<SimpleTreeCellAnnotation>
}