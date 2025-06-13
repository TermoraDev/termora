package app.termora.tree

import app.termora.DynamicIcon
import app.termora.plugin.ExtensionManager
import app.termora.restore
import app.termora.save
import app.termora.setupAntialiasing
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.icons.FlatTreeClosedIcon
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.DefaultTreeCellRenderer

open class SimpleTreeCellRenderer : DefaultTreeCellRenderer() {

    private val extensions
        get() = ExtensionManager.getInstance().getExtensions(SimpleTreeCellRendererExtension::class.java)
    private val annotations = mutableListOf<SimpleTreeCellAnnotation>()

    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any?,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        val c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

        this.icon = null
        if (value is SimpleTreeNode<*>) {
            val icon = value.getIcon(sel, expanded, hasFocus)
            if (icon != null) {
                TreeIcon.icon = icon
                this.icon = TreeIcon
            }
        }

        annotations.clear()
        for (extension in extensions) {
            annotations.addAll(extension.createAnnotations(tree, value, sel, expanded, leaf, row, hasFocus))
        }

        return c
    }

    override fun paint(g: Graphics) {
        super.paint(g)

        if (g is Graphics2D) {
            g.save()
            setupAntialiasing(g)

            var offset = width - annotations.sumOf { it.getWidth(this) + SimpleTreeCellAnnotation.SPACE }

            for (annotation in annotations) {
                g.save()
                g.translate(offset, 0)
                annotation.paint(this, g)
                g.restore()
                offset += annotation.getWidth(this) + SimpleTreeCellAnnotation.SPACE
            }

            g.restore()
        }
    }

    fun getAnnotations(): Array<SimpleTreeCellAnnotation> {
        return annotations.toTypedArray()
    }

    private object TreeIcon : Icon {
        var icon: Icon = FlatTreeClosedIcon()

        /* dark icon default color */
        private val colorFilter = FlatSVGIcon.ColorFilter { color -> Color(206, 208, 214) }

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            if (c is DefaultTreeCellRenderer) {
                if (g.color == c.textSelectionColor) {
                    val icon = this.icon
                    if (icon is DynamicIcon && FlatLaf.isLafDark().not()) {
                        val oldColorFilter = icon.colorFilter
                        icon.colorFilter = colorFilter
                        icon.paintIcon(c, g, x, y)
                        icon.colorFilter = oldColorFilter
                        return
                    }
                }
            }
            icon.paintIcon(c, g, x, y)
        }

        override fun getIconWidth(): Int {
            return icon.iconWidth
        }

        override fun getIconHeight(): Int {
            return icon.iconHeight
        }

    }


}