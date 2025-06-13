package app.termora.tree

import org.apache.commons.lang3.ArrayUtils
import java.util.function.Function
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

class FilterableHostTreeModel(
    private val tree: JTree,
    /**
     * 如果返回 true 则空文件夹也展示
     */
    private val showEmptyFolder: () -> Boolean = { true }
) : TreeModel {
    private val model = tree.model
    private val root = ReferenceTreeNode(model.root)
    private var listeners = emptyArray<TreeModelListener>()
    private var filters = emptyArray<Function<SimpleTreeNode<*>, Boolean>>()
    private val mapping = mutableMapOf<TreeNode, ReferenceTreeNode>()

    init {
        refresh()
        initEvents()
    }


    /**
     * @param a 旧的
     * @param b 新的
     */
    private fun cloneTree(a: SimpleTreeNode<*>, b: DefaultMutableTreeNode) {
        b.removeAllChildren()
        for (c in a.children()) {
            if (c !is SimpleTreeNode<*>) {
                continue
            }

            if (c.isFolder.not()) {
                if (filters.isNotEmpty() && filters.none { it.apply(c) }) {
                    continue
                }
            }

            val n = ReferenceTreeNode(c).apply { mapping[c] = this }.apply { b.add(this) }

            // 文件夹递归复制
            if (c.isFolder) {
                cloneTree(c, n)
            }

            // 如果是文件夹
            if (c.isFolder) {
                if (n.childCount == 0) {
                    if (showEmptyFolder.invoke()) {
                        continue
                    }
                    n.removeFromParent()
                }
            }
        }
    }

    private fun initEvents() {
        model.addTreeModelListener(object : TreeModelListener {
            override fun treeNodesChanged(e: TreeModelEvent) {
                refresh()
            }

            override fun treeNodesInserted(e: TreeModelEvent) {
                refresh()
            }

            override fun treeNodesRemoved(e: TreeModelEvent) {
                refresh()
            }

            override fun treeStructureChanged(e: TreeModelEvent) {
                refresh()
            }
        })
    }

    override fun getRoot(): Any {
        return root.userObject
    }

    override fun getChild(parent: Any, index: Int): Any {
        val c = map(parent)?.getChildAt(index)
        if (c is ReferenceTreeNode) {
            return c.userObject
        }
        throw IndexOutOfBoundsException("Index out of bounds")
    }

    override fun getChildCount(parent: Any): Int {
        return map(parent)?.childCount ?: 0
    }

    private fun map(parent: Any): ReferenceTreeNode? {
        if (parent is TreeNode) {
            return mapping[parent]
        }
        return null
    }

    override fun isLeaf(node: Any?): Boolean {
        return (node as TreeNode).isLeaf
    }

    override fun valueForPathChanged(path: TreePath, newValue: Any) {

    }

    override fun getIndexOfChild(parent: Any, child: Any): Int {
        val c = map(parent) ?: return -1
        for (i in 0 until c.childCount) {
            val e = c.getChildAt(i)
            if (e is ReferenceTreeNode && e.userObject == child) {
                return i
            }
        }
        return -1
    }

    override fun addTreeModelListener(l: TreeModelListener) {
        listeners = ArrayUtils.addAll(listeners, l)
    }

    override fun removeTreeModelListener(l: TreeModelListener) {
        listeners = ArrayUtils.removeElement(listeners, l)
    }

    fun addFilter(f: Function<SimpleTreeNode<*>, Boolean>) {
        filters = ArrayUtils.add(filters, f)
    }

    fun refresh() {
        mapping.clear()
        mapping[model.root as TreeNode] = root
        cloneTree(model.root as HostTreeNode, root)
        SwingUtilities.updateComponentTreeUI(tree)
    }

    fun getModel(): TreeModel {
        return model
    }

    private class ReferenceTreeNode(any: Any) : DefaultMutableTreeNode(any)

}