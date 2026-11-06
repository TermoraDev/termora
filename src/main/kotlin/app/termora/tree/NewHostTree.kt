package app.termora.tree

import app.termora.*
import app.termora.Application.ohMyJson
import app.termora.account.AccountManager
import app.termora.account.TeamRole
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.actions.OpenHostAction
import app.termora.database.DatabaseChangedExtension
import app.termora.database.DatabaseManager
import app.termora.plugin.ExtensionManager
import app.termora.plugin.internal.sftppty.SFTPPtyProtocolProvider
import app.termora.plugin.internal.ssh.SSHProtocolProvider
import app.termora.protocol.TransferProtocolProvider
import app.termora.tag.TagDialog
import app.termora.tag.TagManager
import app.termora.tag.TagSimpleTreeCellRendererExtension
import app.termora.transfer.TransferActionEvent
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.util.SystemInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.filefilter.FileFilterUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.sshd.client.config.hosts.HostConfigEntry
import org.ini4j.Ini
import org.ini4j.Reg
import org.jdesktop.swingx.action.ActionManager
import org.slf4j.LoggerFactory
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.*
import java.io.*
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.*
import javax.swing.*
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

class NewHostTree : SimpleTree(), Disposable {

    companion object {
        private val log = LoggerFactory.getLogger(NewHostTree::class.java)

        init {
            // 基本信息
            ShowMoreInfoSimpleTreeCellRendererExtension.getInstance()
            // 标签
            TagSimpleTreeCellRendererExtension.getInstance()
        }

    }

    private val properties get() = DatabaseManager.getInstance().properties
    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val openHostAction get() = ActionManager.getInstance().getAction(OpenHostAction.OPEN_HOST)
    private val sftpAction get() = ActionManager.getInstance().getAction(app.termora.Actions.SFTP)
    private val enableManager get() = EnableManager.getInstance()
    private var isShowMoreInfo
        get() = enableManager.isShowMoreInfo()
        set(value) = enableManager.setShowMoreInfo(value)
    private var isShowTags
        get() = enableManager.isShowTags()
        set(value) = enableManager.setShowTags(value)
    private var isPopupMenu = false
    override val simpleTreeModel = NewHostTreeModel.getInstance()

    /**
     * 是否允许显示右键菜单
     */
    var contextmenu = true

    /**
     * 是否允许双击连接
     */
    var doubleClickConnection = true

    init {
        initViews()
        initEvents()
    }

    fun getSuperModel(): TreeModel {
        return super.getModel()
    }

    private fun initViews() {
        super.setModel(simpleTreeModel)
        isEditable = true
        dragEnabled = true
        isRootVisible = false
        dropMode = DropMode.ON_OR_INSERT
        selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
    }

    private fun initEvents() {
        // double click
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (doubleClickConnection && SwingUtilities.isLeftMouseButton(e) && e.clickCount % 2 == 0) {
                    val lastNode = lastSelectedPathComponent as? HostTreeNode ?: return
                    if (lastNode.isFolder.not()) {
                        val path = tree.getClosestPathForLocation(e.x, e.y) ?: return
                        val bounds = tree.getRowBounds(tree.getRowForPath(path)) ?: return
                        if ((e.y >= bounds.y && e.y < (bounds.y + bounds.height)).not()) return
                        openHostAction?.actionPerformed(OpenHostActionEvent(e.source, lastNode.host, e))
                    }
                }
            }
        })

        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && doubleClickConnection) {
                    val nodes = getSelectionSimpleTreeNodes()
                    if (nodes.size == 1 && nodes.first().isFolder) {
                        val path = TreePath(simpleTreeModel.getPathToRoot(nodes.first()))
                        if (isExpanded(path)) {
                            collapsePath(path)
                        } else {
                            expandPath(path)
                        }
                    } else {
                        for (node in getSelectionSimpleTreeNodes(true)) {
                            openHostAction?.actionPerformed(OpenHostActionEvent(e.source, node.host, e))
                        }
                    }
                }
            }
        })

        // 开启 ToolTip 功能
        ToolTipManager.sharedInstance().registerComponent(this)

        // 设置鼠标移动提示
        addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val path: TreePath? = getPathForLocation(e.x, e.y)
                if (path != null) {
                    val node: HostTreeNode = path.lastPathComponent as HostTreeNode
                    if (node.host.remark.isNotEmpty()) {
                        toolTipText = node.host.remark
                    } else {
                        toolTipText = null
                    }
                } else {
                    toolTipText = null
                }
            }
        })

        actionMap.put("copy", object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                toolkit.systemClipboard.setContents(StringSelection(StringUtils.EMPTY), null)
                val nodes = getSelectionSimpleTreeNodes(false).toMutableList()
                nodes.removeIf { e -> e.getParents().any { nodes.contains(it) } }
                if (nodes.isEmpty() || nodes.any { it is TeamTreeNode }) return
                if (nodes.any { it.id == "0" || it.id.isBlank() }) return
                toolkit.systemClipboard.setContents(NodesTransferable(nodes), null)
            }
        })

        actionMap.put("paste", object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                val lastNode = getLastSelectedPathNode() ?: return
                val folder = if (lastNode.isFolder) lastNode.parent ?: simpleTreeModel.root
                else lastNode.parent ?: return

                if (toolkit.systemClipboard.isDataFlavorAvailable(NodesTransferable.FLAVOR).not()) return
                val nodes = (toolkit.systemClipboard.getData(NodesTransferable.FLAVOR) as? List<*>)
                    ?.filterIsInstance<HostTreeNode>() ?: return

                for (node in nodes) {
                    val newNode = copyNode(node, folder.id)
                    // 复制的是文件夹，就在最后面
                    if (newNode.isFolder) {
                        simpleTreeModel.insertNodeInto(newNode, folder, folder.folderCount)
                    } else if (lastNode.isFolder) { // 用户选的节点是文件夹，那就在最后一个child下面
                        simpleTreeModel.insertNodeInto(newNode, folder, folder.childCount)
                    } else { // 用户选的是主机并且复制的是主机
                        simpleTreeModel.insertNodeInto(newNode, folder, folder.getIndex(lastNode) + 1)
                    }
                }
            }
        })

    }

    fun restoreExpansions() {
        val name = super.getName()
        if (name.isNullOrBlank()) {
            return
        }
        val state = properties.getString("${name}.state") ?: return
        TreeUtils.loadExpansionState(this, state)
    }

    override fun dispose() {
        // 销毁
        ToolTipManager.sharedInstance().unregisterComponent(this)

        val name = super.getName()
        if (name.isNullOrBlank().not()) {
            properties.putString("${name}.state", TreeUtils.saveExpansionState(this))
        }
        super.setModel(null)
    }

    override fun canImport(support: TransferHandler.TransferSupport): Boolean {
        val dropLocation = support.dropLocation as? DropLocation ?: return false
        val node = dropLocation.path.lastPathComponent as? SimpleTreeNode<*> ?: return false
        return node != simpleTreeModel.getRoot()
    }

    override fun canCreateTransferable(c: JComponent): Boolean {
        val nodes = getSelectionSimpleTreeNodes(false)
        return nodes.none { it is TeamTreeNode || it.id == "0" }
    }

    override fun showContextmenu(evt: MouseEvent) {
        if (!contextmenu) return
        val lastNode = lastSelectedPathComponent
        if (lastNode !is HostTreeNode) return


        val tags = TagManager.getInstance().getTags(lastNode.host.ownerId)
        val nodes = getSelectionSimpleTreeNodes()
        val fullNodes = getSelectionSimpleTreeNodes(true)
        val lastNodeParent = lastNode.parent ?: simpleTreeModel.root
        val lastHost = lastNode.host
        val hasTeamNode = nodes.any { it is TeamTreeNode }

        val popupMenu = FlatPopupMenu()
        val newMenu = JMenu(I18n.getString("termora.welcome.contextmenu.new"))
        val newFolder = newMenu.add(I18n.getString("termora.welcome.contextmenu.new.folder"))
        val newHost = newMenu.add(I18n.getString("termora.welcome.contextmenu.new.host"))
        val importMenu = JMenu(I18n.getString("termora.welcome.contextmenu.import"))
        val csvMenu = importMenu.add("CSV")
        val xShellMenu = importMenu.add("Xshell")
        val puTTYMenu = importMenu.add("PuTTY")
        val electermMenu = importMenu.add("electerm")
        val finalShellMenu = importMenu.add("FinalShell")
        val windTermMenu = importMenu.add("WindTerm")
        val secureCRTMenu = importMenu.add("SecureCRT")
        val sshMenu = importMenu.add(".ssh/config")
        val mobaXtermMenu = importMenu.add("MobaXterm")

        // 为了避免误导，如果是 SSH 右键时显示 SFTP
        val sftpText = if (SSHProtocolProvider.PROTOCOL.equals(lastHost.protocol, true))
            "SFTP" else I18n.getString("termora.transport.sftp")
        val open = popupMenu.add(I18n.getString("termora.welcome.contextmenu.connect"))
        val openWith = popupMenu.add(JMenu(I18n.getString("termora.welcome.contextmenu.connect-with"))) as JMenu
        val openWithSFTP = openWith.add(sftpText)
        val openWithSFTPCommand = openWith.add(I18n.getString("termora.tabbed.contextmenu.sftp-command"))
        val openInNewWindow = popupMenu.add(I18n.getString("termora.welcome.contextmenu.open-in-new-window"))
        popupMenu.addSeparator()
        val copy = popupMenu.add(I18n.getString("termora.welcome.contextmenu.copy"))
        val remove = popupMenu.add(I18n.getString("termora.welcome.contextmenu.remove"))
        val rename = popupMenu.add(I18n.getString("termora.welcome.contextmenu.rename"))
        popupMenu.addSeparator()
        val refresh = popupMenu.add(I18n.getString("termora.welcome.contextmenu.refresh"))
        val expandAll = popupMenu.add(I18n.getString("termora.welcome.contextmenu.expand-all"))
        val colspanAll = popupMenu.add(I18n.getString("termora.welcome.contextmenu.collapse-all"))
        popupMenu.addSeparator()
        popupMenu.add(importMenu)
        popupMenu.add(newMenu)
        popupMenu.addSeparator()
        val tagsMenu = popupMenu.add(JMenu(I18n.getString("termora.tag"))) as JMenu
        val showMenu = popupMenu.add(JMenu(I18n.getString("termora.welcome.contextmenu.show"))) as JMenu
        val showMoreInfo = showMenu.add(JCheckBoxMenuItem(I18n.getString("termora.welcome.contextmenu.show.more-info")))
        val showTags = showMenu.add(JCheckBoxMenuItem(I18n.getString("termora.welcome.contextmenu.show.tags")))
        for (extension in ExtensionManager.getInstance().getExtensions(HostTreeShowMoreEnableExtension::class.java)) {
            showMenu.add(extension.createJCheckBoxMenuItem(this))
        }
        showTags.isSelected = isShowTags
        showMoreInfo.isSelected = isShowMoreInfo
        showMoreInfo.addActionListener { isShowMoreInfo = !isShowMoreInfo }
        showTags.addActionListener { isShowTags = !isShowTags }
        val property = popupMenu.add(I18n.getString("termora.welcome.contextmenu.property"))

        xShellMenu.addActionListener { HostTreeImporter(this).importHosts(lastNode, HostTreeImporter.ImportType.Xshell) }
        puTTYMenu.addActionListener { HostTreeImporter(this).importHosts(lastNode, HostTreeImporter.ImportType.PuTTY) }
        secureCRTMenu.addActionListener { HostTreeImporter(this).importHosts(lastNode, HostTreeImporter.ImportType.SecureCRT) }
        electermMenu.addActionListener { HostTreeImporter(this).importHosts(lastNode, HostTreeImporter.ImportType.electerm) }
        mobaXtermMenu.addActionListener { HostTreeImporter(this).importHosts(lastNode, HostTreeImporter.ImportType.MobaXterm) }
        sshMenu.addActionListener { HostTreeImporter(this).importHosts(lastNode, HostTreeImporter.ImportType.SSH) }
        finalShellMenu.addActionListener { HostTreeImporter(this).importHosts(lastNode, HostTreeImporter.ImportType.FinalShell) }
        csvMenu.addActionListener { HostTreeImporter(this).importHosts(lastNode, HostTreeImporter.ImportType.CSV) }
        windTermMenu.addActionListener { HostTreeImporter(this).importHosts(lastNode, HostTreeImporter.ImportType.WindTerm) }
        open.addActionListener { openHosts(it, false) }
        openInNewWindow.addActionListener { openHosts(it, true) }
        openWithSFTP.addActionListener { openWithSFTP(it) }
        openWithSFTPCommand.addActionListener { openWithSFTPCommand(it) }
        newFolder.addActionListener {
            val host = Host(
                id = randomUUID(),
                protocol = "Folder",
                ownerId = lastNode.host.ownerId,
                ownerType = lastNode.host.ownerType,
                name = I18n.getString("termora.welcome.contextmenu.new.folder.name"),
                sort = lastNode.folderCount.toLong(),
                parentId = lastNode.id,
            )
            val node = HostTreeNode(host)
            simpleTreeModel.insertNodeInto(node, lastNode, lastNode.folderCount)
            selectionPath = TreePath(simpleTreeModel.getPathToRoot(node))
            startEditingAtPath(selectionPath)
        }
        remove.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
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
                        simpleTreeModel.removeNodeFromParent(c)
                    }
                }
            }
        })
        copy.addActionListener {
            for (c in nodes) {
                val p = c.parent ?: continue
                val newNode = copyNode(c, p.host.id)
                // 先入 Model
                simpleTreeModel.insertNodeInto(newNode, p, lastNodeParent.getIndex(c) + 1)
                // 开启编辑
                selectionPath = TreePath(simpleTreeModel.getPathToRoot(newNode))
            }
        }
        rename.addActionListener { startEditingAtPath(TreePath(simpleTreeModel.getPathToRoot(lastNode))) }
        expandAll.addActionListener {
            for (node in fullNodes) {
                expandPath(TreePath(simpleTreeModel.getPathToRoot(node)))
            }
        }
        colspanAll.addActionListener {
            for (node in fullNodes.reversed()) {
                collapsePath(TreePath(simpleTreeModel.getPathToRoot(node)))
            }
        }
        newHost.addActionListener(object : ActionListener {
            private val accountManager get() = AccountManager.getInstance()
            override fun actionPerformed(e: ActionEvent) {
                val dialog = NewHostDialogV2(
                    owner,
                    accountOwner = accountManager.getOwners().first { it.id == lastHost.ownerId })
                dialog.setLocationRelativeTo(owner)
                dialog.isVisible = true
                val host = (dialog.host ?: return).copy(
                    parentId = lastHost.id,
                    ownerId = lastNode.host.ownerId,
                    ownerType = lastNode.host.ownerType,
                )

                val newNode = HostTreeNode(host)
                simpleTreeModel.insertNodeInto(newNode, lastNode, lastNode.childCount)
                selectionPath = TreePath(simpleTreeModel.getPathToRoot(newNode))
            }
        })
        property.addActionListener(object : ActionListener {
            private val accountManager get() = AccountManager.getInstance()
            override fun actionPerformed(e: ActionEvent) {
                val dialog = NewHostDialogV2(
                    owner,
                    lastHost,
                    accountOwner = accountManager.getOwners().first { it.id == lastHost.ownerId })
                dialog.setLocationRelativeTo(owner)
                dialog.title = lastHost.name
                dialog.isVisible = true
                val host = dialog.host ?: return
                lastNode.host = host
                simpleTreeModel.nodeStructureChanged(lastNode)
            }
        })
        refresh.addActionListener { simpleTreeModel.reload(lastNode) }

        newMenu.isEnabled = lastNode.isFolder
        remove.isEnabled = getSelectionSimpleTreeNodes().none { it.id == "0" } && hasTeamNode.not()
        copy.isEnabled = remove.isEnabled
        rename.isEnabled = remove.isEnabled
        property.isEnabled = lastNode.isFolder.not() && hasTeamNode.not()
        refresh.isEnabled = lastNode.isFolder
        importMenu.isEnabled = lastNode.isFolder

        val canConnect = getPathForLocation(evt.x, evt.y) != null &&
            (ConnectionSettings.allowFolderConnections || nodes.none { it.isFolder })
        open.isEnabled = canConnect && fullNodes.any { !it.isFolder }
        openInNewWindow.isEnabled = open.isEnabled

        // 如果选中了 SSH 服务器，那么才启用
        openWithSFTP.isEnabled = canConnect && fullNodes.map { it.host }.any { TransferProtocolProvider.valueOf(it.protocol) != null }
        openWithSFTPCommand.isEnabled = canConnect && fullNodes.map { it.host }.any { it.protocol == SSHProtocolProvider.PROTOCOL }
        openWith.isEnabled = openWith.menuComponents.any { it is JMenuItem && it.isEnabled }


        for (tag in tags) {
            val menu = tagsMenu.add(JMenuItem(tag.text))
            val isSelected = lastHost.options.tags.contains(tag.id)
            menu.isEnabled = remove.isEnabled
            menu.isSelected = isSelected
            if (menu.isEnabled) {
                menu.icon = CheckBoxMenuItemColorIcon(ColorIcon(color = ColorHash.hash(tag.id)), isSelected)
            }
            menu.addActionListener {
                val tags = lastHost.options.tags.toMutableList()
                if (isSelected) {
                    tags.removeAll { it == tag.id }
                } else {
                    tags.add(tag.id)
                }
                lastNode.host = lastHost.copy(options = lastHost.options.copy(tags = tags))
                simpleTreeModel.nodeStructureChanged(lastNode)
            }
        }

        if (tags.isNotEmpty()) {
            tagsMenu.addSeparator()
        }

        tagsMenu.add(I18n.getString("termora.tag.manage-tags")).addActionListener {
            val dialog = TagDialog(owner, lastHost.ownerId)
            dialog.setLocationRelativeTo(owner)
            dialog.isVisible = true
        }

        popupMenu.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
                isPopupMenu = true
            }

            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
                isPopupMenu = false
            }

            override fun popupMenuCanceled(e: PopupMenuEvent?) {
            }

        })

        popupMenu.addPopupMenuListener(object : PopupMenuListener {

            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
                for (node in nodes) {
                    val team = TeamTreeNode.parentTeam(node) ?: continue
                    if (team.role == TeamRole.Visitor.name) {
                        copy.isEnabled = false
                        remove.isEnabled = false
                        rename.isEnabled = false
                        importMenu.isEnabled = false
                        newMenu.isEnabled = false
                        tagsMenu.isEnabled = false
                        break
                    }
                }
            }

            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {

            }

            override fun popupMenuCanceled(e: PopupMenuEvent?) {

            }

        })

        val mnemonics = mapOf(
            refresh to KeyEvent.VK_R,
            newMenu to KeyEvent.VK_W,
            newFolder to KeyEvent.VK_F,
            rename to KeyEvent.VK_M,
            remove to KeyEvent.VK_D,
            property to KeyEvent.VK_I,
        )

        for ((item, mnemonic) in mnemonics) {
            item.text = "${item.text}(${KeyEvent.getKeyText(mnemonic)})"
            item.setMnemonic(mnemonic)
        }

        popupMenu.show(this, evt.x, evt.y)
    }

    override fun onRenamed(node: SimpleTreeNode<*>, text: String) {
        val lastNode = node as? HostTreeNode ?: return
        lastNode.host = lastNode.host.copy(name = text)
        simpleTreeModel.nodeStructureChanged(lastNode)
    }

    override fun createTreeModelListener(): TreeModelListener {
        return object : XTreeModelHandler() {
            override fun treeStructureChanged(e: TreeModelEvent) {
                SwingUtilities.updateComponentTreeUI(tree)
            }

        }
    }

    override fun rebase(node: SimpleTreeNode<*>, parent: SimpleTreeNode<*>, index: Int) {
        if (parent !is HostTreeNode || node !is HostTreeNode) return
        // 从原来的父移除
        simpleTreeModel.removeNodeFromParent(node)

        node.data = node.data.copy(
            id = randomUUID(),
            parentId = parent.id,
            ownerId = parent.host.ownerId,
            ownerType = parent.host.ownerType,
        )

        simpleTreeModel.insertNodeInto(node, parent, index)

        // 子也需要变基
        for ((idx, e) in node.childrenNode().withIndex()) {
            rebase(e, node, idx)
        }


    }


    private fun copyNode(
        node: HostTreeNode,
        parentId: String,
        idGenerator: () -> String = { randomUUID() },
        level: Int = 0
    ): HostTreeNode {

        val host = node.host
        val now = host.sort + 1
        val name = if (level == 0) "${host.name} ${I18n.getString("termora.welcome.contextmenu.copy")}"
        else host.name

        val newHost = host.copy(
            id = idGenerator.invoke(),
            name = name,
            parentId = parentId,
            sort = now
        )
        val newNode = HostTreeNode(newHost)

        if (host.isFolder) {
            for (child in node.children()) {
                if (child is HostTreeNode) {
                    simpleTreeModel.insertNodeInto(
                        copyNode(child, newHost.id, idGenerator, level + 1),
                        newNode, node.getIndex(child)
                    )
                }
            }
        }

        return newNode

    }

    override fun getSelectionSimpleTreeNodes(include: Boolean): List<HostTreeNode> {
        return super.getSelectionSimpleTreeNodes(include).filterIsInstance<HostTreeNode>()
    }


    private fun openHosts(evt: EventObject, openInNewWindow: Boolean) {
        if (!ConnectionSettings.allowFolderConnections && getSelectionSimpleTreeNodes().any { it.isFolder }) return
        assertEventDispatchThread()
        val nodes = getSelectionSimpleTreeNodes(true).filter { it.isFolder.not() }
        if (nodes.isEmpty()) return
        val source = if (openInNewWindow)
            TermoraFrameManager.getInstance().createWindow().apply { isVisible = true }
        else evt.source
        nodes.map { it.host }.forEach { openHostAction.actionPerformed(OpenHostActionEvent(source, it, evt)) }
    }

    private fun openWithSFTP(evt: EventObject) {
        if (!ConnectionSettings.allowFolderConnections && getSelectionSimpleTreeNodes().any { it.isFolder }) return
        val hosts = getSelectionSimpleTreeNodes(true)
            .map { it.host }.filter { TransferProtocolProvider.valueOf(it.protocol) != null }
        if (hosts.isEmpty()) return

        for (host in hosts) {
            sftpAction.actionPerformed(TransferActionEvent(this, host, evt))
        }
    }

    private fun openWithSFTPCommand(evt: EventObject) {
        if (!ConnectionSettings.allowFolderConnections && getSelectionSimpleTreeNodes().any { it.isFolder }) return
        val nodes =
            getSelectionSimpleTreeNodes(true).map { it.host }.filter { it.protocol == SSHProtocolProvider.PROTOCOL }
        if (nodes.isEmpty()) return
        for (host in nodes) {
            openHostAction.actionPerformed(
                OpenHostActionEvent(
                    this,
                    host.copy(protocol = SFTPPtyProtocolProvider.PROTOCOL),
                    evt
                )
            )
        }
    }

    private class NodesTransferable(val nodes: List<HostTreeNode>) : Transferable {
        companion object {
            val FLAVOR = DataFlavor("termora/host-tree", "Termora host tree transfers")
        }

        override fun getTransferDataFlavors(): Array<out DataFlavor> {
            return arrayOf(FLAVOR)
        }

        override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean {
            return flavor == FLAVOR
        }

        override fun getTransferData(flavor: DataFlavor?): Any {
            return if (flavor == FLAVOR) nodes else throw UnsupportedFlavorException(flavor)
        }

    }

}
