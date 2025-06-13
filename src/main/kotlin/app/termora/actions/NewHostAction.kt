package app.termora.actions

import app.termora.NewHostDialogV2
import app.termora.tree.FilterableHostTreeModel
import app.termora.tree.HostTreeNode
import app.termora.tree.NewHostTreeModel
import javax.swing.tree.TreePath

class NewHostAction : AnAction() {
    companion object {

        /**
         * 添加主机对话框
         */
        const val NEW_HOST = "NewHostAction"

    }

    override fun actionPerformed(evt: AnActionEvent) {
        val tree = evt.getData(DataProviders.Welcome.HostTree) ?: return
        var lastNode = (tree.lastSelectedPathComponent ?: tree.model.root) as? HostTreeNode ?: return
        if (lastNode.host.isFolder.not()) {
            lastNode = lastNode.parent ?: return
        }

        // Root 不可以添加，如果是 Root 那么加到用户下
        if (lastNode == tree.model.root) {
            lastNode = lastNode.childrenNode().firstOrNull { it.id == "0" || it.id.isBlank() } ?: return
        }

        val lastHost = lastNode.host
        val dialog = NewHostDialogV2(evt.window)
        dialog.setLocationRelativeTo(evt.window)
        dialog.isVisible = true
        val host = (dialog.host ?: return).copy(
            parentId = lastHost.id,
            ownerId = lastHost.ownerId,
            ownerType = lastHost.ownerType
        )

        val newNode = HostTreeNode(host)
        val model = if (tree.model is FilterableHostTreeModel) (tree.model as FilterableHostTreeModel).getModel()
        else tree.model

        if (model is NewHostTreeModel) {
            model.insertNodeInto(newNode, lastNode, lastNode.childCount)
            tree.selectionPath = TreePath(model.getPathToRoot(newNode))
        }

    }
}