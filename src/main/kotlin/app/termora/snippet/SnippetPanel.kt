package app.termora.snippet

import app.termora.Disposable
import app.termora.DocumentAdaptor
import app.termora.DynamicColor
import app.termora.database.DatabaseManager
import app.termora.tree.TreeUtils
import com.formdev.flatlaf.extras.components.FlatTextArea
import com.formdev.flatlaf.ui.FlatRoundBorder
import com.formdev.flatlaf.util.SystemInfo
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.undo.UndoManager


class SnippetPanel : JPanel(BorderLayout()), Disposable {
    companion object {
        private val log = LoggerFactory.getLogger(SnippetPanel::class.java)
        private val properties get() = DatabaseManager.getInstance().properties
        private val snippetManager get() = SnippetManager.getInstance()
    }

    private val leftPanel = JPanel(BorderLayout())
    private val cardLayout = CardLayout()
    private val rightPanel = JPanel(cardLayout)
    private val snippetTree = SnippetTree()
    private val editor = SnippetEditor()
    private val lastNode get() = snippetTree.getLastSelectedPathNode()

    init {
        initViews()
        initEvents()
    }

    private fun initViews() {
        val splitPane = JSplitPane()
        splitPane.border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor)
        val scrollPane = JScrollPane(snippetTree)
        scrollPane.border = BorderFactory.createEmptyBorder()

        leftPanel.add(scrollPane, BorderLayout.CENTER)
        leftPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, DynamicColor.BorderColor),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)
        )
        leftPanel.preferredSize = Dimension(
            properties.getString("SnippetPanel.LeftPanel.width", "180").toIntOrNull() ?: 180,
            -1
        )
        leftPanel.minimumSize = Dimension(leftPanel.preferredSize.width, leftPanel.preferredSize.height)

        rightPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, DynamicColor.BorderColor),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)
        )

        val bannerPanel = JPanel(BorderLayout())
        bannerPanel.add(SnippetBannerPanel(), BorderLayout.CENTER)
        bannerPanel.border = BorderFactory.createEmptyBorder(32, 0, 0, 0)
        rightPanel.add(bannerPanel, "Banner")
        rightPanel.add(editor, "Editor")

        splitPane.leftComponent = leftPanel
        splitPane.rightComponent = rightPanel
        add(splitPane, BorderLayout.CENTER)

        cardLayout.show(rightPanel, "Banner")

    }

    private fun initEvents() {
        snippetTree.addTreeSelectionListener {
            val lastNode = this.lastNode
            if (lastNode == null || lastNode.isFolder) {
                cardLayout.show(rightPanel, "Banner")
            } else {
                cardLayout.show(rightPanel, "Editor")
                editor.textArea.text = lastNode.data.snippet
                editor.resetUndo()
            }
        }

        SwingUtilities.invokeLater {
            if (snippetTree.selectionRows?.isEmpty() == true) {
                snippetTree.addSelectionRow(0)
            }
            snippetTree.requestFocusInWindow()
        }


        val expansionState = properties.getString("SnippetPanel.LeftTreePanel.expansionState", StringUtils.EMPTY)
        if (expansionState.isNotBlank()) {
            TreeUtils.loadExpansionState(snippetTree, expansionState)
        }

        val selectionRows = properties.getString("SnippetPanel.LeftTreePanel.selectionRows", StringUtils.EMPTY)
        if (selectionRows.isNotBlank()) {
            TreeUtils.loadSelectionRows(snippetTree, selectionRows)
        }
    }

    override fun dispose() {
        properties.putString("SnippetPanel.LeftPanel.width", leftPanel.width.toString())
        properties.putString("SnippetPanel.LeftPanel.height", leftPanel.height.toString())
        properties.putString("SnippetPanel.LeftTreePanel.expansionState", TreeUtils.saveExpansionState(snippetTree))
        properties.putString("SnippetPanel.LeftTreePanel.selectionRows", TreeUtils.saveSelectionRows(snippetTree))
    }


    private inner class SnippetEditor : JPanel(BorderLayout()) {
        val textArea = FlatTextArea()
        private var undoManager = UndoManager()

        init {
            initViews()
            initEvents()
        }

        private fun initViews() {
            val panel = JPanel(BorderLayout())
            panel.add(JScrollPane(textArea).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)
            panel.border = FlatRoundBorder()
            add(panel, BorderLayout.CENTER)
            add(createTip(), BorderLayout.SOUTH)

            textArea.setFocusTraversalKeys(
                KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .getDefaultFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)
            )
            textArea.setFocusTraversalKeys(
                KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
                KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .getDefaultFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS)
            )
        }

        private fun initEvents() {
            textArea.document.addUndoableEditListener(undoManager)

            textArea.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if ((e.keyCode == KeyEvent.VK_Z || e.keyCode == KeyEvent.VK_Y) && (if (SystemInfo.isMacOS) e.isMetaDown else e.isControlDown)) {
                        try {
                            if (e.keyCode == KeyEvent.VK_Z) {
                                if (undoManager.canUndo()) {
                                    undoManager.undo()
                                }
                            } else {
                                if (undoManager.canRedo()) {
                                    undoManager.redo()
                                }
                            }
                        } catch (cue: Exception) {
                            if (log.isErrorEnabled) {
                                log.error(cue.message, cue.message)
                            }
                        }
                    }
                }
            })

            textArea.document.addDocumentListener(object : DocumentAdaptor() {
                override fun changedUpdate(e: DocumentEvent) {
                    val lastNode = lastNode ?: return
                    lastNode.data = lastNode.data.copy(snippet = textArea.text, updateDate = System.currentTimeMillis())
                    snippetManager.addSnippet(lastNode.data)
                }
            })
        }

        fun resetUndo() {
            textArea.document.removeUndoableEditListener(undoManager)
            undoManager = UndoManager()
            textArea.document.addUndoableEditListener(undoManager)
        }

        private fun createTip(): JPanel {
            val formMargin = "10dlu"
            val panel = FormBuilder.create().debug(false)
                .border(
                    BorderFactory.createCompoundBorder(
                        FlatRoundBorder(),
                        BorderFactory.createEmptyBorder(2, 4, 4, 4)
                    )
                )
                .layout(
                    FormLayout(
                        "left:pref, left:pref, $formMargin, left:pref, left:pref, $formMargin, left:pref, left:pref",
                        "pref, $formMargin, pref"
                    )
                )
                .add(createTipLabel("\\r - ")).xy(1, 1)
                .add(createTipLabel("CR")).xy(2, 1)
                .add(createTipLabel("\\n - ")).xy(4, 1)
                .add(createTipLabel("LF")).xy(5, 1)
                .add(createTipLabel("\\t - ")).xy(7, 1)
                .add(createTipLabel("Tab")).xy(8, 1)

                .add(createTipLabel("\\a - ")).xy(1, 2)
                .add(createTipLabel("Bell")).xy(2, 2)
                .add(createTipLabel("\\e - ")).xy(4, 2)
                .add(createTipLabel("Escape")).xy(5, 2)
                .add(createTipLabel("\\b - ")).xy(7, 2)
                .add(createTipLabel("Backspace")).xy(8, 2)
                .build()

            return JPanel(BorderLayout()).apply {
                add(panel, BorderLayout.CENTER)
                border = BorderFactory.createEmptyBorder(4, 0, 0, 0)
            }
        }

        private fun createTipLabel(text: String): JLabel {
            val label = JLabel(text)
            label.foreground = UIManager.getColor("textInactiveText")
            return label
        }


    }
}