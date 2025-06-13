package app.termora.snippet

import app.termora.I18n
import app.termora.OptionPane
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.tree.SimpleTree
import app.termora.tree.SimpleTreeNode
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import java.awt.event.MouseEvent
import javax.swing.DropMode
import javax.swing.JMenu
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.tree.TreePath

class SnippetTree : SimpleTree() {
    override val model = SnippetTreeModel()

    private val snippetManager get() = SnippetManager.getInstance()

    init {
        initViews()
        initEvents()
    }

    private fun initViews() {
        super.setModel(model)
        isEditable = true
        dragEnabled = true
        dropMode = DropMode.ON_OR_INSERT
    }

    private fun initEvents() {

    }

    override fun showContextmenu(evt: MouseEvent) {
        val lastNode = getLastSelectedPathNode() ?: return
        val popupMenu = FlatPopupMenu()
        val newMenu = JMenu(I18n.getString("termora.welcome.contextmenu.new"))
        val newFolder = newMenu.add(I18n.getString("termora.welcome.contextmenu.new.folder"))
        val newSnippet = newMenu.add(I18n.getString("termora.snippet"))
        val rename = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.rename"))
        val remove = popupMenu.add(I18n.getString("termora.welcome.contextmenu.remove"))
        popupMenu.addSeparator()
        val refresh = popupMenu.add(I18n.getString("termora.welcome.contextmenu.refresh"))
        val expandAll = popupMenu.add(I18n.getString("termora.welcome.contextmenu.expand-all"))
        val colspanAll = popupMenu.add(I18n.getString("termora.welcome.contextmenu.collapse-all"))
        popupMenu.addSeparator()

        newFolder.addActionListener {
            val snippet = Snippet(
                name = I18n.getString("termora.welcome.contextmenu.new.folder.name"),
                type = SnippetType.Folder,
                parentId = lastNode.data.id
            )
            snippetManager.addSnippet(snippet)
            newFolder(SnippetTreeNode(snippet))
        }
        newSnippet.addActionListener {
            val snippet = Snippet(
                name = I18n.getString("termora.snippet"),
                type = SnippetType.Snippet,
                parentId = lastNode.data.id
            )
            snippetManager.addSnippet(snippet)
            newFile(SnippetTreeNode(snippet))
        }

        rename.addActionListener { startEditingAtPath(TreePath(model.getPathToRoot(lastNode))) }
        refresh.addActionListener { model.reload(lastNode) }
        expandAll.addActionListener {
            for (node in getSelectionSimpleTreeNodes(true)) {
                expandPath(TreePath(model.getPathToRoot(node)))
            }
        }
        colspanAll.addActionListener {
            for (node in getSelectionSimpleTreeNodes(true).reversed()) {
                collapsePath(TreePath(model.getPathToRoot(node)))
            }
        }
        remove.addActionListener(object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                val nodes = getSelectionSimpleTreeNodes()
                if (nodes.isEmpty()) return
                if (OptionPane.showConfirmDialog(
                        SwingUtilities.getWindowAncestor(tree),
                        I18n.getString("termora.keymgr.delete-warning"),
                        I18n.getString("termora.remove"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                    ) == JOptionPane.YES_OPTION
                ) {
                    for (c in nodes) {
                        snippetManager.addSnippet(c.data.copy(deleted = true, updateDate = System.currentTimeMillis()))
                        model.removeNodeFromParent(c)
                        // 将所有子孙也删除
                        for (child in c.getAllChildren()) {
                            snippetManager.addSnippet(
                                child.data.copy(
                                    deleted = true,
                                    updateDate = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            }
        })


        rename.isEnabled = lastNode != model.root
        remove.isEnabled = rename.isEnabled
        newFolder.isEnabled = lastNode.data.type == SnippetType.Folder
        newSnippet.isEnabled = newFolder.isEnabled
        newMenu.isEnabled = newFolder.isEnabled
        refresh.isEnabled = newFolder.isEnabled

        popupMenu.add(newMenu)

        popupMenu.show(this, evt.x, evt.y)
    }

    public override fun getLastSelectedPathNode(): SnippetTreeNode? {
        return super.getLastSelectedPathNode() as? SnippetTreeNode
    }

    override fun onRenamed(node: SimpleTreeNode<*>, text: String) {
        val n = node as? SnippetTreeNode ?: return
        n.data = n.data.copy(name = text, updateDate = System.currentTimeMillis())
        snippetManager.addSnippet(n.data)
        model.nodeStructureChanged(n)
    }

    override fun rebase(node: SimpleTreeNode<*>, parent: SimpleTreeNode<*>, index: Int) {
        // 从原来的父移除
        model.removeNodeFromParent(node)

        val nNode = node as? SnippetTreeNode ?: return
        val nParent = parent as? SnippetTreeNode ?: return
        nNode.data = nNode.data.copy(parentId = nParent.data.id, updateDate = System.currentTimeMillis())

        model.insertNodeInto(nNode, nParent, index)
    }

    override fun getSelectionSimpleTreeNodes(include: Boolean): List<SnippetTreeNode> {
        return super.getSelectionSimpleTreeNodes(include).filterIsInstance<SnippetTreeNode>()
    }
}