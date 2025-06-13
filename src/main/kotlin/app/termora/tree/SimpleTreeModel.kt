package app.termora.tree

import javax.swing.tree.DefaultTreeModel

abstract class SimpleTreeModel<T>(root: SimpleTreeNode<T>) : DefaultTreeModel(root) {
    @Suppress("UNCHECKED_CAST")
    override fun getRoot(): SimpleTreeNode<T> {
        return super.getRoot() as SimpleTreeNode<T>
    }

}