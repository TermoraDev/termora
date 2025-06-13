package app.termora.tree

import app.termora.Host
import app.termora.Icons
import app.termora.protocol.ProtocolProvider
import com.formdev.flatlaf.icons.FlatTreeClosedIcon
import com.formdev.flatlaf.icons.FlatTreeOpenIcon
import javax.swing.Icon
import javax.swing.tree.TreeNode

open class HostTreeNode(host: Host) : SimpleTreeNode<Host>(host) {

    var host: Host
        get() = data
        set(value) = setUserObject(value)

    override val isFolder: Boolean
        get() = host.isFolder

    override val id: String
        get() = host.id

    /**
     * 如果要重新赋值，记得修改 [Host.updateDate] 否则下次取出时可能时缓存的
     */
    override var data: Host
        get() {
            return userObject as Host
        }
        set(value) = setUserObject(value)

    override val folderCount
        get() = children().toList().filterIsInstance<SimpleTreeNode<*>>().count { it.isFolder }

    override fun getParent(): HostTreeNode? {
        return super.getParent() as HostTreeNode?
    }

    override fun getAllChildren(): List<HostTreeNode> {
        return super.getAllChildren().filterIsInstance<HostTreeNode>()
    }

    fun findChild(id: String): HostTreeNode? {
        return getAllChildren().firstOrNull { it.id == id }
    }

    override fun getIcon(selected: Boolean, expanded: Boolean, hasFocus: Boolean): Icon {
        // user root
        if (id == "0" || id.isBlank()) {
            return if (selected && hasFocus) Icons.user.dark else Icons.user
        }

        if (host.isFolder) return if (expanded) FlatTreeOpenIcon() else FlatTreeClosedIcon()
        val icon = ProtocolProvider.valueOf(host.protocol)?.getIcon() ?: Icons.terminal
        return if (selected && hasFocus) icon.dark else icon
    }

    fun childrenNode(): List<HostTreeNode> {
        return children?.map { it as HostTreeNode } ?: emptyList()
    }


    /**
     * 深度克隆
     * @param scopes 克隆的范围
     */
    fun clone(scopes: Set<String> = emptySet()): HostTreeNode {
        val newNode = clone() as HostTreeNode
        deepClone(newNode, this, scopes)
        return newNode
    }

    private fun deepClone(newNode: HostTreeNode, oldNode: HostTreeNode, scopes: Set<String> = emptySet()) {
        for (child in oldNode.childrenNode()) {
            if (scopes.isNotEmpty() && !scopes.contains(child.data.protocol)) continue
            val newChildNode = child.clone() as HostTreeNode
            deepClone(newChildNode, child, scopes)
            newNode.add(newChildNode)
        }
    }

    override fun clone(): Any {
        val newNode = HostTreeNode(data)
        newNode.children = null
        newNode.parent = null
        return newNode
    }

    override fun isNodeChild(aNode: TreeNode?): Boolean {
        if (aNode is HostTreeNode) {
            for (node in childrenNode()) {
                if (node.data == aNode.data) {
                    return true
                }
            }
        }
        return super.isNodeChild(aNode)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HostTreeNode

        return data == other.data
    }

    override fun hashCode(): Int {
        return data.hashCode()
    }

    override fun toString(): String {
        return host.name
    }
}