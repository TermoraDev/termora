package app.termora.tree

import app.termora.*
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import app.termora.plugin.internal.rdp.RDPProtocolProvider
import app.termora.plugin.internal.serial.SerialProtocolProvider
import app.termora.plugin.internal.ssh.SSHProtocolProvider
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
            text = "(${node.getAllChildren().size})"
        } else if (node is HostTreeNode) {
            val host = node.host
            if (host.protocol == SSHProtocolProvider.PROTOCOL || host.protocol == RDPProtocolProvider.PROTOCOL) {
                text = if (host.name.contains(host.host)) {
                    host.username
                } else {
                    "${host.username}@${host.host}"
                }
            } else if (host.protocol == SerialProtocolProvider.PROTOCOL) {
                text = host.options.serialComm.port
            }
        }

        return listOf(MyMarkerSimpleTreeCellAnnotation(text))
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