package app.termora

import app.termora.db.Database
import java.awt.Dimension
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*
import javax.swing.tree.TreeSelectionModel

class HostTreeDialog(owner: Window) : DialogWrapper(owner) {

    private val tree = HostTree()

    val hosts = mutableListOf<Host>()

    var allowMulti = true
        set(value) {
            field = value
            if (value) {
                tree.selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
            } else {
                tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            }
        }

    init {
        size = Dimension(UIManager.getInt("Dialog.width") - 200, UIManager.getInt("Dialog.height") - 150)
        isModal = true
        isResizable = false
        controlsVisible = false
        title = I18n.getString("termora.transport.sftp.select-host")

        tree.setModel(SearchableHostTreeModel(tree.model) { host ->
            host.protocol == Protocol.Folder || host.protocol == Protocol.SSH
        })
        tree.contextmenu = true
        tree.doubleClickConnection = false
        tree.dragEnabled = false

        initEvents()

        init()
        setLocationRelativeTo(null)

    }

    private fun initEvents() {
        addWindowListener(object : WindowAdapter() {
            override fun windowActivated(e: WindowEvent) {
                removeWindowListener(this)
                val state = Database.instance.properties.getString("HostTreeDialog.HostTreeExpansionState")
                if (state != null) {
                    TreeUtils.loadExpansionState(tree, state)
                }
            }
        })

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount % 2 == 0) {
                    val node = tree.lastSelectedPathComponent ?: return
                    if (node is Host && node.protocol != Protocol.Folder) {
                        doOKAction()
                    }
                }
            }
        })

        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {
                Database.instance.properties.putString(
                    "HostTreeDialog.HostTreeExpansionState",
                    TreeUtils.saveExpansionState(tree)
                )
            }
        })
    }

    override fun createCenterPanel(): JComponent {
        val scrollPane = JScrollPane(tree)
        scrollPane.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)
        )

        return scrollPane
    }

    override fun doOKAction() {

        if (allowMulti) {
            val nodes = tree.getSelectionNodes().filter { it.protocol == Protocol.SSH }
            if (nodes.isEmpty()) {
                return
            }
            hosts.clear()
            hosts.addAll(nodes)
        } else {
            val node = tree.lastSelectedPathComponent ?: return
            if (node !is Host || node.protocol != Protocol.SSH) {
                return
            }
            hosts.clear()
            hosts.add(node)
        }


        super.doOKAction()
    }

    override fun doCancelAction() {
        hosts.clear()
        super.doCancelAction()
    }

}