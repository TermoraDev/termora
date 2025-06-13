package app.termora.snippet

import app.termora.I18n
import app.termora.tree.SimpleTreeModel
import javax.swing.tree.MutableTreeNode
import javax.swing.tree.TreeNode
import kotlin.math.min

class SnippetTreeModel : SimpleTreeModel<Snippet>(
    SnippetTreeNode(
        Snippet(
            id = "0",
            name = I18n.getString("termora.snippet.title"),
            type = SnippetType.Folder
        )
    )
) {

    private val snippetManager get() = SnippetManager.getInstance()

    init {
        reload()
    }

    override fun getRoot(): SnippetTreeNode {
        return super.getRoot() as SnippetTreeNode
    }

    override fun reload(parent: TreeNode?) {

        if (parent !is SnippetTreeNode) {
            super.reload(parent)
            return
        }

        parent.removeAllChildren()

        val hosts = snippetManager.snippets()
        val nodes = linkedMapOf<String, SnippetTreeNode>()

        // 遍历 Host 列表，构建树节点
        for (host in hosts) {
            val node = SnippetTreeNode(host)
            nodes[host.id] = node
        }

        for (host in hosts) {
            val node = nodes[host.id] ?: continue
            if (host.parentId.isBlank()) continue
            val p = nodes[host.parentId] ?: continue
            p.add(node)
        }

        for ((_, v) in nodes.entries) {
            if (parent.data.id == v.data.parentId) {
                parent.add(v)
            }
        }

        super.reload(parent)
    }

    override fun insertNodeInto(newChild: MutableTreeNode, parent: MutableTreeNode, index: Int) {
        super.insertNodeInto(newChild, parent, min(index, parent.childCount))

        if (newChild is SnippetTreeNode) {
            snippetManager.addSnippet(newChild.data)
        }

        // 重置所有排序
        if (parent is SnippetTreeNode) {
            for ((i, c) in parent.children().toList().filterIsInstance<SnippetTreeNode>().withIndex()) {
                val sort = i.toLong()
                if (c.data.sort == sort) continue
                c.data = c.data.copy(sort = sort, updateDate = System.currentTimeMillis())
                snippetManager.addSnippet(c.data)
            }
        }
    }
}