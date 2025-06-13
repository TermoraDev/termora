package app.termora.snippet

import app.termora.DialogWrapper
import app.termora.Disposable
import app.termora.Disposer
import app.termora.I18n
import app.termora.database.DatabaseManager
import app.termora.tree.TreeUtils
import org.apache.commons.lang3.StringUtils
import java.awt.Dimension
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

class SnippetTreeDialog(owner: Window) : DialogWrapper(owner) {
    private val snippetTree = SnippetTree()
    private val properties get() = DatabaseManager.getInstance().properties

    var lastNode: SnippetTreeNode? = null

    init {
        size = Dimension(360, 380)
        title = I18n.getString("termora.snippet.title")
        isModal = true
        isResizable = true
        controlsVisible = false
        setLocationRelativeTo(null)
        init()

        snippetTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount % 2 == 0) {
                    val node = snippetTree.getLastSelectedPathNode() ?: return
                    if (node.isFolder) return
                    doOKAction()
                }
            }
        })

        Disposer.register(disposable, object : Disposable {
            override fun dispose() {
                properties.putString("SnippetTreeDialog.Tree.expansionState", TreeUtils.saveExpansionState(snippetTree))
                properties.putString("SnippetTreeDialog.Tree.selectionRows", TreeUtils.saveSelectionRows(snippetTree))
            }
        })


        val expansionState = properties.getString("SnippetTreeDialog.Tree.expansionState", StringUtils.EMPTY)
        if (expansionState.isNotBlank()) {
            TreeUtils.loadExpansionState(snippetTree, expansionState)
        }

        val selectionRows = properties.getString("SnippetTreeDialog.Tree.selectionRows", StringUtils.EMPTY)
        if (selectionRows.isNotBlank()) {
            TreeUtils.loadSelectionRows(snippetTree, selectionRows)
        }
    }

    override fun createCenterPanel(): JComponent {
        return JScrollPane(snippetTree).apply { border = BorderFactory.createEmptyBorder(0, 6, 6, 6) }
    }

    override fun doCancelAction() {
        lastNode = null
        super.doCancelAction()
    }

    override fun doOKAction() {
        val node = snippetTree.getLastSelectedPathNode() ?: return
        if (node.isFolder) return
        lastNode = node
        super.doOKAction()
    }

    fun getSelectedNode(): SnippetTreeNode? {
        return lastNode
    }
}