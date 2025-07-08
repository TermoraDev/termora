package app.termora.tree

import app.termora.*
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import app.termora.plugin.internal.rdp.RDPProtocolProvider
import app.termora.plugin.internal.ssh.SSHProtocolProvider
import app.termora.plugin.internal.wsl.WSLProtocolProvider
import org.apache.commons.lang3.StringUtils
import java.awt.Graphics2D
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.DefaultTreeCellRenderer

/**
 * 显示更多信息的扩展
 */
class ShowMoreInfoSimpleTreeCellRendererExtension private constructor() : SimpleTreeCellRendererExtension, Disposable {

    private val isShowMoreInfo
        get() = EnableManager.getInstance().isShowMoreInfo()

    companion object {
        fun getInstance(): ShowMoreInfoSimpleTreeCellRendererExtension {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(ShowMoreInfoSimpleTreeCellRendererExtension::class) { ShowMoreInfoSimpleTreeCellRendererExtension() }
        }
    }

    init {
        DynamicExtensionHandler.getInstance()
            .register(SimpleTreeCellRendererExtension::class.java, this)
            .let { Disposer.register(this, it) }
    }

    // wsl
    // key: guid
    // value: name
    private val map = mutableMapOf<String, String?>()

    @Suppress("CascadeIf")
    override fun createAnnotations(
        tree: JTree,
        value: Any?,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): List<SimpleTreeCellAnnotation> {
        if (isShowMoreInfo.not()) return emptyList()
        val node = value as? SimpleTreeNode<*> ?: return emptyList()

        var text = StringUtils.EMPTY

        if (node.isFolder) {
            text = "(${getChildrenCount(tree, node)})"
        } else if (node is HostTreeNode) {
            val host = node.host
            if (host.protocol == SSHProtocolProvider.PROTOCOL || host.protocol == RDPProtocolProvider.PROTOCOL) {
                text = if (host.name.contains(host.host)) {
                    host.username
                } else {
                    "${host.username}@${host.host}"
                }
            } else if (host.protocol == WSLProtocolProvider.PROTOCOL) {
                text = host.host
            }
        }

        return listOf(MyMarkerSimpleTreeCellAnnotation(text))
    }

    private fun getChildrenCount(tree: JTree, node: SimpleTreeNode<*>): Int {
        if (tree is NewHostTree) {
            val model = tree.getSuperModel()
            var count = 0

            val queue = ArrayDeque<Any>()
            queue.add(node)
            while (queue.isNotEmpty()) {
                val e = queue.removeFirst()
                val childrenCount = model.getChildCount(e)
                for (i in 0 until childrenCount) {
                    queue.addLast(model.getChild(e, i))
                }
                count++
            }

            return count - 1
        }
        return node.getAllChildren().size
    }

    /**
     * 优先级最高
     */
    override fun ordered(): Long {
        return Long.MIN_VALUE
    }

    private class MyMarkerSimpleTreeCellAnnotation(text: String) : MarkerSimpleTreeCellAnnotation(text, fontSize = 0f) {
        override fun paint(c: JComponent, g: Graphics2D) {
            if (c is DefaultTreeCellRenderer) {
                if (g.color == c.textNonSelectionColor) {
                    foreground = DynamicColor("textInactiveText")
                }
            }
            super.paint(c, g)
        }
    }
}