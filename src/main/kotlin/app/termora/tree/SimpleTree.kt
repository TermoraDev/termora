package app.termora.tree

import app.termora.OutlineTextField
import com.formdev.flatlaf.ui.FlatTreeUI
import org.jdesktop.swingx.JXTree
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*
import javax.swing.event.CellEditorListener
import javax.swing.event.ChangeEvent
import javax.swing.plaf.TreeUI
import javax.swing.tree.AbstractLayoutCache
import javax.swing.tree.TreePath
import kotlin.math.min


open class SimpleTree : JXTree() {

    protected open val model get() = super.getModel() as SimpleTreeModel<*>
    private val editor = OutlineTextField(64)
    protected val tree get() = this

    init {
        initViews()
        initEvents()
    }


    private fun initViews() {

        // renderer
        setCellRenderer(SimpleTreeCellRenderer())

        // rename
        setCellEditor(object : DefaultCellEditor(editor) {
            override fun isCellEditable(e: EventObject?): Boolean {
                if (e is MouseEvent || !tree.isCellEditable(e)) {
                    return false
                }

                return super.isCellEditable(e).apply {
                    if (this) {
                        editor.preferredSize = Dimension(min(220, width - 64), 0)
                    }
                }
            }

            override fun getCellEditorValue(): Any? {
                return getLastSelectedPathNode()?.data
            }


        })
    }

    private fun initEvents() {
        // 右键选中
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (!SwingUtilities.isRightMouseButton(e)) {
                    return
                }

                requestFocusInWindow()

                val selectionRows = selectionModel.selectionRows

                val selRow = getClosestRowForLocation(e.x, e.y)
                if (selRow < 0) {
                    selectionModel.clearSelection()
                    return
                } else if (selectionRows != null && selectionRows.contains(selRow)) {
                    return
                }

                selectionPath = getPathForLocation(e.x, e.y)

                setSelectionRow(selRow)
            }

        })

        // contextmenu
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (!(SwingUtilities.isRightMouseButton(e))) {
                    return
                }

                if (Objects.isNull(lastSelectedPathComponent)) {
                    return
                }

                showContextmenu(e)
            }

        })

        // rename
        getCellEditor().addCellEditorListener(object : CellEditorListener {
            override fun editingStopped(e: ChangeEvent) {
                val node = getLastSelectedPathNode() ?: return
                if (editor.text.isBlank() || editor.text == node.toString()) {
                    return
                }
                onRenamed(node, editor.text)
            }

            override fun editingCanceled(e: ChangeEvent) {
            }
        })

        // drag
        transferHandler = object : TransferHandler() {

            override fun createTransferable(c: JComponent): Transferable? {
                if (tree.canCreateTransferable(c).not()) return null
                val nodes = getSelectionSimpleTreeNodes().toMutableList()
                if (nodes.isEmpty()) return null
                if (nodes.contains(model.root)) return null

                val iterator = nodes.iterator()
                while (iterator.hasNext()) {
                    val node = iterator.next()
                    val parents = model.getPathToRoot(node).filter { it != node }
                    if (parents.any { nodes.contains(it) }) {
                        iterator.remove()
                    }
                }

                return MoveNodeTransferable(nodes)
            }

            override fun getSourceActions(c: JComponent?): Int {
                return MOVE
            }

            override fun canImport(support: TransferSupport): Boolean {
                if (support.component != tree) return false
                if (tree.canImport(support).not()) return false
                val dropLocation = support.dropLocation as? JTree.DropLocation ?: return false
                val path = dropLocation.path ?: return false
                val node = path.lastPathComponent as? SimpleTreeNode<*> ?: return false
                if (!support.isDataFlavorSupported(MoveNodeTransferable.dataFlavor)) return false
                val nodes = (support.transferable.getTransferData(MoveNodeTransferable.dataFlavor) as? List<*>)
                    ?.filterIsInstance<SimpleTreeNode<*>>() ?: return false
                if (nodes.isEmpty()) return false
                if (!node.isFolder) return false

                for (e in nodes) {
                    // 禁止拖拽到自己的子下面
                    if (path.equals(TreePath(e.path)) || TreePath(e.path).isDescendant(path)) {
                        return false
                    }

                    // 文件夹只能拖拽到文件夹的下面
                    if (e.isFolder) {
                        if (dropLocation.childIndex > node.folderCount) {
                            return false
                        }
                    } else if (dropLocation.childIndex != -1) {
                        // 非文件夹也不能拖拽到文件夹的上面
                        if (dropLocation.childIndex < node.folderCount) {
                            return false
                        }
                    }

                    val p = e.parent ?: continue
                    // 如果是同级目录排序，那么判断是不是自己的上下，如果是的话也禁止
                    if (p == node && dropLocation.childIndex != -1) {
                        val idx = p.getIndex(e)
                        if (dropLocation.childIndex in idx..idx + 1) {
                            return false
                        }
                    }
                }

                support.setShowDropLocation(true)

                return true
            }

            override fun importData(support: TransferSupport): Boolean {
                if (!support.isDrop) return false
                val dropLocation = support.dropLocation as? JTree.DropLocation ?: return false
                val node = dropLocation.path.lastPathComponent as? SimpleTreeNode<*> ?: return false
                val nodes = (support.transferable.getTransferData(MoveNodeTransferable.dataFlavor) as? List<*>)
                    ?.filterIsInstance<SimpleTreeNode<*>>() ?: return false

                // 转移
                for (e in nodes) {

                    val index = if (dropLocation.childIndex == -1) {
                        if (e.isFolder) {
                            node.folderCount
                        } else {
                            node.childCount
                        }
                    } else {
                        if (e.isFolder) {
                            min(node.folderCount, dropLocation.childIndex)
                        } else {
                            min(node.childCount, dropLocation.childIndex)
                        }
                    }

                    rebase(e, node, min(index, node.childCount))
                    selectionPath = TreePath(model.getPathToRoot(e))
                }

                // 先展开最顶级的
                expandPath(TreePath(model.getPathToRoot(node)))

                return true
            }
        }
    }

    protected open fun canImport(support: TransferHandler.TransferSupport): Boolean {
        return true
    }

    protected open fun canCreateTransferable(c: JComponent): Boolean {
        return true
    }

    protected open fun newFolder(newNode: SimpleTreeNode<*>): Boolean {
        val lastNode = lastSelectedPathComponent
        if (lastNode !is SimpleTreeNode<*>) return false
        return newNode(newNode, lastNode.folderCount)
    }

    protected open fun newFile(newNode: SimpleTreeNode<*>): Boolean {
        val lastNode = lastSelectedPathComponent
        if (lastNode !is SimpleTreeNode<*>) return false
        return newNode(newNode, lastNode.childCount)
    }

    private fun newNode(newNode: SimpleTreeNode<*>, index: Int): Boolean {
        val lastNode = lastSelectedPathComponent
        if (lastNode !is SimpleTreeNode<*>) return false
        model.insertNodeInto(newNode, lastNode, index)
        selectionPath = TreePath(model.getPathToRoot(newNode))
        startEditingAtPath(selectionPath)
        return true
    }

    open fun getLastSelectedPathNode(): SimpleTreeNode<*>? {
        return lastSelectedPathComponent as? SimpleTreeNode<*>
    }


    protected open fun showContextmenu(evt: MouseEvent) {

    }

    protected open fun onRenamed(node: SimpleTreeNode<*>, text: String) {}

    /**
     * 包含孙子
     */
    open fun getSelectionSimpleTreeNodes(include: Boolean = false): List<SimpleTreeNode<*>> {
        val paths = selectionPaths ?: return emptyList()
        if (paths.isEmpty()) return emptyList()
        val nodes = mutableListOf<SimpleTreeNode<*>>()
        val parents = paths.mapNotNull { it.lastPathComponent }
            .filterIsInstance<SimpleTreeNode<*>>().toMutableList()

        if (include) {
            while (parents.isNotEmpty()) {
                val node = parents.removeFirst()
                nodes.add(node)
                parents.addAll(node.children().toList().filterIsInstance<SimpleTreeNode<*>>())
            }
        }

        return if (include) nodes else parents
    }

    protected open fun isCellEditable(e: EventObject?): Boolean {
        return getLastSelectedPathNode() != model.root
    }

    protected open fun rebase(node: SimpleTreeNode<*>, parent: SimpleTreeNode<*>, index: Int) {

    }

    private class MoveNodeTransferable(val nodes: List<SimpleTreeNode<*>>) : Transferable {
        companion object {
            val dataFlavor =
                DataFlavor("${DataFlavor.javaJVMLocalObjectMimeType};class=${MoveNodeTransferable::class.java.name}")
        }


        override fun getTransferDataFlavors(): Array<DataFlavor> {
            return arrayOf(dataFlavor)
        }

        override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean {
            return dataFlavor == flavor
        }

        override fun getTransferData(flavor: DataFlavor?): Any {
            if (flavor == dataFlavor) {
                return nodes
            }
            throw UnsupportedFlavorException(flavor)
        }

    }

    override fun setUI(ui: TreeUI) {
        if (ui is MyTreeUI) super.setUI(ui)
    }

    override fun updateUI() {
        super.setUI(MyTreeUI())
        super.updateUI()
    }

    private inner class MyTreeUI : FlatTreeUI() {

        override fun createNodeDimensions(): AbstractLayoutCache.NodeDimensions? {
            return object : NodeDimensionsHandler() {
                override fun getNodeDimensions(
                    value: Any?, row: Int, depth: Int, expanded: Boolean,
                    size: Rectangle?
                ): Rectangle {
                    val dimensions = super.getNodeDimensions(
                        value, row,
                        depth, expanded, size
                    )

                    val renderer = wrappedCellRenderer
                    if (renderer is SimpleTreeCellRenderer) {
                        val c = renderer.getTreeCellRendererComponent(
                            tree, value, tree.isRowSelected(row), expanded,
                            model.isLeaf(value), row, tree.hasFocus()
                        )
                        if (c is JComponent) {
                            val annotations = renderer.getAnnotations()
                            for (annotation in annotations) {
                                dimensions.width += annotation.getWidth(c) + SimpleTreeCellAnnotation.SPACE
                            }
                        }
                    }

                    return dimensions
                }
            }
        }

    }
}