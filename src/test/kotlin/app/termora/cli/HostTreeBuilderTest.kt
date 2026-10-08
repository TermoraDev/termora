package app.termora.cli

import app.termora.Host
import app.termora.plugin.internal.cli.HostTreeBuilder
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HostTreeBuilderTest {
    private fun folder(id: String, name: String, parent: String = "0", sort: Long = 0) =
        Host(id = id, name = name, protocol = "Folder", parentId = parent, sort = sort)

    private fun host(id: String, name: String, parent: String = "0", sort: Long = 0) =
        Host(id = id, name = name, protocol = "SSH", host = "10.0.0.1", port = 22, username = "root", parentId = parent, sort = sort)

    private fun childHosts(node: DefaultMutableTreeNode): List<Host> =
        (0 until node.childCount).map { (node.getChildAt(it) as DefaultMutableTreeNode).userObject as Host }

    @Test
    fun `顶层主机挂在 root 下`() {
        val root = HostTreeBuilder.build(listOf(host("a", "web-01")))
        assertEquals(1, root.childCount)
        assertEquals("web-01", childHosts(root)[0].name)
    }

    @Test
    fun `主机挂在其所属文件夹下`() {
        val root = HostTreeBuilder.build(listOf(folder("f", "生产"), host("a", "web-01", parent = "f")))
        assertEquals(1, root.childCount)
        val folderNode = root.getChildAt(0) as DefaultMutableTreeNode
        assertEquals("生产", (folderNode.userObject as Host).name)
        assertEquals(listOf("web-01"), childHosts(folderNode).map { it.name })
    }

    @Test
    fun `过滤已删除节点`() {
        val root = HostTreeBuilder.build(listOf(host("a", "web-01"), host("b", "old").copy(deleted = true)))
        assertEquals(listOf("web-01"), childHosts(root).map { it.name })
    }

    @Test
    fun `同层文件夹优先于主机并按 sort 排序`() {
        val root = HostTreeBuilder.build(
            listOf(
                host("h", "host-z", sort = 0),
                folder("f", "folder-a", sort = 5),
            )
        )
        // 文件夹优先，即使其 sort 更大
        val names = (0 until root.childCount).map { ((root.getChildAt(it) as DefaultMutableTreeNode).userObject as Host).name }
        assertEquals(listOf("folder-a", "host-z"), names)
    }

    @Test
    fun `父不存在的孤儿挂到 root`() {
        val root = HostTreeBuilder.build(listOf(host("a", "web-01", parent = "missing")))
        assertTrue(childHosts(root).any { it.name == "web-01" })
    }
}
