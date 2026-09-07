package app.termora.plugin.internal.cli

import app.termora.Host
import javax.swing.tree.DefaultMutableTreeNode

/**
 * 把扁平的 List<Host> 按 parentId 构建成树。
 * - 过滤 deleted
 * - 同层：文件夹优先，再按 sort 升序
 * - parentId 为 "0"/空 或指向不存在的父：挂到 root
 * 返回一个不含 userObject 的虚拟 root，其子节点为顶层文件夹/主机；每个真实节点的 userObject 是 Host。
 */
object HostTreeBuilder {
    fun build(hosts: List<Host>): DefaultMutableTreeNode {
        val visible = hosts.filter { !it.deleted }
        val nodes = visible.associate { it.id to DefaultMutableTreeNode(it) }
        val root = DefaultMutableTreeNode()

        val ordered = visible.sortedWith(
            compareBy<Host> { if (it.isFolder) 0 else 1 }.thenBy { it.sort }.thenBy { it.name }
        )
        for (host in ordered) {
            val node = nodes.getValue(host.id)
            val parent = if (host.parentId == "0" || host.parentId.isBlank()) root
            else nodes[host.parentId] ?: root
            parent.add(node)
        }
        return root
    }
}
