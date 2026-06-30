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
    override val simpleTreeModel = SnippetTreeModel()

    private val snippetManager get() = SnippetManager.getInstance()

    init {
        initViews()
        initEvents()
    }

    private fun initViews() {
        super.setModel(simpleTreeModel)
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
        val exportSnippets = popupMenu.add(I18n.getString("termora.snippet.export"))
        val importSnippets = popupMenu.add(I18n.getString("termora.snippet.import"))
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

        rename.addActionListener { startEditingAtPath(TreePath(simpleTreeModel.getPathToRoot(lastNode))) }
        refresh.addActionListener { simpleTreeModel.reload(lastNode) }
        expandAll.addActionListener {
            for (node in getSelectionSimpleTreeNodes(true)) {
                expandPath(TreePath(simpleTreeModel.getPathToRoot(node)))
            }
        }
        colspanAll.addActionListener {
            for (node in getSelectionSimpleTreeNodes(true).reversed()) {
                collapsePath(TreePath(simpleTreeModel.getPathToRoot(node)))
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
                        simpleTreeModel.removeNodeFromParent(c)
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

        exportSnippets.addActionListener {
            val fileChooser = javax.swing.JFileChooser()
            fileChooser.dialogTitle = I18n.getString("termora.snippet.export")
            fileChooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("JSON Files", "json")
            fileChooser.selectedFile = java.io.File("snippets-${System.currentTimeMillis()}.json")

            if (fileChooser.showSaveDialog(SwingUtilities.getWindowAncestor(this)) == javax.swing.JFileChooser.APPROVE_OPTION) {
                try {
                    var file = fileChooser.selectedFile
                    if (!file.name.endsWith(".json")) {
                        file = java.io.File(file.parentFile, file.name + ".json")
                    }
                    snippetManager.exportSnippets(file)
                    OptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(this),
                        I18n.getString("termora.snippet.export.success", file.absolutePath),
                        I18n.getString("termora.snippet.export"),
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } catch (e: Exception) {
                    OptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(this),
                        I18n.getString("termora.snippet.export.error", e.message ?: "Unknown error"),
                        I18n.getString("termora.snippet.export"),
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }

        importSnippets.addActionListener {
            val fileChooser = javax.swing.JFileChooser()
            fileChooser.dialogTitle = I18n.getString("termora.snippet.import")
            fileChooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("JSON Files", "json")

            if (fileChooser.showOpenDialog(SwingUtilities.getWindowAncestor(this)) == javax.swing.JFileChooser.APPROVE_OPTION) {
                try {
                    val file = fileChooser.selectedFile

                    // 询问导入模式
                    val options: Array<Any> = arrayOf(
                        I18n.getString("termora.snippet.import.merge.merge"),
                        I18n.getString("termora.snippet.import.merge.replace")
                    )
                    val choice = OptionPane.showConfirmDialog(
                        SwingUtilities.getWindowAncestor(this),
                        I18n.getString("termora.snippet.import.merge.message"),
                        I18n.getString("termora.snippet.import.merge.title"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        options,
                        options[0]
                    )

                    if (choice == -1) return@addActionListener

                    val replaceAll = (choice == 1)
                    val count = snippetManager.importSnippets(file, replaceAll)

                    // 刷新树
                    simpleTreeModel.reload()

                    OptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(this),
                        I18n.getString("termora.snippet.import.success", count),
                        I18n.getString("termora.snippet.import"),
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } catch (e: Exception) {
                    OptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(this),
                        I18n.getString("termora.snippet.import.error", e.message ?: "Unknown error"),
                        I18n.getString("termora.snippet.import"),
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }


        rename.isEnabled = lastNode != simpleTreeModel.root
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
        simpleTreeModel.nodeStructureChanged(n)
    }

    override fun rebase(node: SimpleTreeNode<*>, parent: SimpleTreeNode<*>, index: Int) {
        // 从原来的父移除
        simpleTreeModel.removeNodeFromParent(node)

        val nNode = node as? SnippetTreeNode ?: return
        val nParent = parent as? SnippetTreeNode ?: return
        nNode.data = nNode.data.copy(parentId = nParent.data.id, updateDate = System.currentTimeMillis())

        simpleTreeModel.insertNodeInto(nNode, nParent, index)
    }

    override fun getSelectionSimpleTreeNodes(include: Boolean): List<SnippetTreeNode> {
        return super.getSelectionSimpleTreeNodes(include).filterIsInstance<SnippetTreeNode>()
    }
}