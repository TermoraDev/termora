package app.termora

import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

class SearchableHostTreeModel(
    private val model: HostTreeModel,
    private val filter: (host: Host) -> Boolean = { true }
) : TreeModel {
    private var text = String()

    override fun getRoot(): Any {
        return model.root
    }

    override fun getChild(parent: Any?, index: Int): Any {
        return getChildren(parent)[index]
    }

    override fun getChildCount(parent: Any?): Int {
        return getChildren(parent).size
    }

    override fun isLeaf(node: Any?): Boolean {
        return model.isLeaf(node)
    }

    override fun valueForPathChanged(path: TreePath?, newValue: Any?) {
        return model.valueForPathChanged(path, newValue)
    }

    override fun getIndexOfChild(parent: Any?, child: Any?): Int {
        return getChildren(parent).indexOf(child)
    }

    override fun addTreeModelListener(l: TreeModelListener) {
        model.addTreeModelListener(l)
    }

    override fun removeTreeModelListener(l: TreeModelListener) {
        model.removeTreeModelListener(l)
    }


    private fun getChildren(parent: Any?): List<Host> {
        val children = model.getChildren(parent)
        if (children.isEmpty()) return emptyList()
        return children.filter { e ->
            filter.invoke(e) && e.name.contains(text, true) || TreeUtils.children(model, e, true)
                .filterIsInstance<Host>().any {
                it.name.contains(text, true)
            }
        }
    }

    fun search(text: String) {
        this.text = text
        model.listeners.forEach {
            it.treeStructureChanged(
                TreeModelEvent(
                    this, TreePath(root),
                    null, null
                )
            )
        }
    }

}