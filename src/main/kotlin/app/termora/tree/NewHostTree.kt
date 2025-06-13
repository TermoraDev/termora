package app.termora.tree

import app.termora.*
import app.termora.Application.ohMyJson
import app.termora.actions.OpenHostAction
import app.termora.database.DatabaseChangedExtension
import app.termora.database.DatabaseManager
import app.termora.plugin.ExtensionManager
import app.termora.plugin.internal.sftppty.SFTPPtyProtocolProvider
import app.termora.plugin.internal.ssh.SSHProtocolProvider
import app.termora.sftp.SFTPActionEvent
import app.termora.tag.TagDialog
import app.termora.tag.TagManager
import app.termora.tag.TagSimpleTreeCellRendererExtension
import com.formdev.flatlaf.extras.components.FlatPopupMenu
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
import java.awt.event.*
import java.io.*
import java.util.*
import javax.swing.*
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

class NewHostTree : SimpleTree(), Disposable {

    companion object {
        private val log = LoggerFactory.getLogger(NewHostTree::class.java)
        private val CSV_HEADERS = arrayOf("Folders", "Label", "Hostname", "Port", "Username", "Protocol")

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
    override val model = NewHostTreeModel.getInstance()

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


    private fun initViews() {
        super.setModel(model)
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
                        val path = TreePath(model.getPathToRoot(nodes.first()))
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
        val name = super.getName()
        if (name.isNullOrBlank().not()) {
            properties.putString("${name}.state", TreeUtils.saveExpansionState(this))
        }
        super.setModel(null)
    }

    override fun canImport(support: TransferHandler.TransferSupport): Boolean {
        val dropLocation = support.dropLocation as? DropLocation ?: return false
        val node = dropLocation.path.lastPathComponent as? SimpleTreeNode<*> ?: return false
        return node != model.getRoot()
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
        val lastNodeParent = lastNode.parent ?: model.root
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


        val open = popupMenu.add(I18n.getString("termora.welcome.contextmenu.connect"))
        val openWith = popupMenu.add(JMenu(I18n.getString("termora.welcome.contextmenu.connect-with"))) as JMenu
        val openWithSFTP = openWith.add(I18n.getString("termora.transport.sftp"))
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

        xShellMenu.addActionListener { importHosts(lastNode, ImportType.Xshell) }
        puTTYMenu.addActionListener { importHosts(lastNode, ImportType.PuTTY) }
        secureCRTMenu.addActionListener { importHosts(lastNode, ImportType.SecureCRT) }
        electermMenu.addActionListener { importHosts(lastNode, ImportType.electerm) }
        mobaXtermMenu.addActionListener { importHosts(lastNode, ImportType.MobaXterm) }
        sshMenu.addActionListener { importHosts(lastNode, ImportType.SSH) }
        finalShellMenu.addActionListener { importHosts(lastNode, ImportType.FinalShell) }
        csvMenu.addActionListener { importHosts(lastNode, ImportType.CSV) }
        windTermMenu.addActionListener { importHosts(lastNode, ImportType.WindTerm) }
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
            model.insertNodeInto(node, lastNode, lastNode.folderCount)
            selectionPath = TreePath(model.getPathToRoot(node))
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
                        model.removeNodeFromParent(c)
                    }
                }
            }
        })
        copy.addActionListener {
            for (c in nodes) {
                val p = c.parent ?: continue
                val newNode = copyNode(c, p.host.id)
                // 先入 Model
                model.insertNodeInto(newNode, p, lastNodeParent.getIndex(c) + 1)
                // 开启编辑
                selectionPath = TreePath(model.getPathToRoot(newNode))
            }
        }
        rename.addActionListener { startEditingAtPath(TreePath(model.getPathToRoot(lastNode))) }
        expandAll.addActionListener {
            for (node in fullNodes) {
                expandPath(TreePath(model.getPathToRoot(node)))
            }
        }
        colspanAll.addActionListener {
            for (node in fullNodes.reversed()) {
                collapsePath(TreePath(model.getPathToRoot(node)))
            }
        }
        newHost.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                val dialog = NewHostDialogV2(owner)
                dialog.setLocationRelativeTo(owner)
                dialog.isVisible = true
                val host = (dialog.host ?: return).copy(
                    parentId = lastHost.id,
                    ownerId = lastNode.host.ownerId,
                    ownerType = lastNode.host.ownerType,
                )

                val newNode = HostTreeNode(host)
                model.insertNodeInto(newNode, lastNode, lastNode.childCount)
                selectionPath = TreePath(model.getPathToRoot(newNode))
            }
        })
        property.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                val dialog = NewHostDialogV2(owner, lastHost)
                dialog.setLocationRelativeTo(owner)
                dialog.title = lastHost.name
                dialog.isVisible = true
                val host = dialog.host ?: return
                lastNode.host = host
                model.nodeStructureChanged(lastNode)
            }
        })
        refresh.addActionListener { model.reload(lastNode) }

        newMenu.isEnabled = lastNode.isFolder
        remove.isEnabled = getSelectionSimpleTreeNodes().none { it.id == "0" } && hasTeamNode.not()
        copy.isEnabled = remove.isEnabled
        rename.isEnabled = remove.isEnabled
        property.isEnabled = lastNode.isFolder.not() && hasTeamNode.not()
        refresh.isEnabled = lastNode.isFolder
        importMenu.isEnabled = lastNode.isFolder

        // 如果选中了 SSH 服务器，那么才启用
        openWithSFTP.isEnabled = fullNodes.map { it.host }.any { it.protocol == SSHProtocolProvider.PROTOCOL }
        openWithSFTPCommand.isEnabled = openWithSFTP.isEnabled
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
                model.nodeStructureChanged(lastNode)
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

        popupMenu.show(this, evt.x, evt.y)
    }

    override fun onRenamed(node: SimpleTreeNode<*>, text: String) {
        val lastNode = node as? HostTreeNode ?: return
        lastNode.host = lastNode.host.copy(name = text)
        model.nodeStructureChanged(lastNode)
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
        model.removeNodeFromParent(node)

        node.data = node.data.copy(
            id = randomUUID(),
            parentId = parent.id,
            ownerId = parent.host.ownerId,
            ownerType = parent.host.ownerType,
        )

        model.insertNodeInto(node, parent, index)

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
                    model.insertNodeInto(
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
        assertEventDispatchThread()
        val nodes = getSelectionSimpleTreeNodes(true).filter { it.isFolder.not() }
        if (nodes.isEmpty()) return
        val source = if (openInNewWindow)
            TermoraFrameManager.Companion.getInstance().createWindow().apply { isVisible = true }
        else evt.source
        nodes.map { it.host }.forEach { openHostAction.actionPerformed(OpenHostActionEvent(source, it, evt)) }
    }

    private fun openWithSFTP(evt: EventObject) {
        val nodes =
            getSelectionSimpleTreeNodes(true).map { it.host }.filter { it.protocol == SSHProtocolProvider.PROTOCOL }
        if (nodes.isEmpty()) return

        for (node in nodes) {
            sftpAction.actionPerformed(SFTPActionEvent(this, node.id, evt))
        }
    }

    private fun openWithSFTPCommand(evt: EventObject) {
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

    private fun importHosts(folder: HostTreeNode, type: ImportType) {
        try {
            doImportHosts(folder, type)
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
            OptionPane.showMessageDialog(owner, ExceptionUtils.getMessage(e), messageType = JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun doImportHosts(folder: HostTreeNode, type: ImportType) {
        val chooser = JFileChooser()
        chooser.fileSelectionMode = JFileChooser.FILES_ONLY
        chooser.isAcceptAllFileFilterUsed = false
        chooser.isMultiSelectionEnabled = false

        when (type) {
            ImportType.WindTerm -> chooser.fileFilter = FileNameExtensionFilter("WindTerm (*.sessions)", "sessions")
            ImportType.SSH -> chooser.fileFilter = FileNameExtensionFilter("SSH (config)", "config")
            ImportType.CSV -> chooser.fileFilter = FileNameExtensionFilter("CSV (*.csv)", "csv")
            ImportType.SecureCRT -> chooser.fileFilter = FileNameExtensionFilter("SecureCRT (*.xml)", "xml")
            ImportType.electerm -> chooser.fileFilter = FileNameExtensionFilter("electerm (*.json)", "json")
            ImportType.PuTTY -> chooser.fileFilter = FileNameExtensionFilter("PuTTY (*.reg)", "reg")
            ImportType.MobaXterm -> chooser.fileFilter =
                FileNameExtensionFilter("MobaXterm (*.mobaconf,*.ini)", "ini", "mobaconf")

            ImportType.Xshell -> {
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                chooser.dialogTitle = "Xshell Sessions"
                chooser.isAcceptAllFileFilterUsed = true
            }

            ImportType.FinalShell -> {
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                chooser.isAcceptAllFileFilterUsed = true
            }
        }

        val dir = properties.getString("NewHostTree.ImportHosts.defaultDir", StringUtils.EMPTY)
        if (dir.isNotBlank()) {
            val file = FileUtils.getFile(dir)
            if (file.exists()) {
                chooser.currentDirectory = file
            }
        }

        // csv template
        if (type == ImportType.CSV) {
            val code = OptionPane.showConfirmDialog(
                owner,
                I18n.getString("termora.welcome.contextmenu.import.csv.download-template"),
                optionType = JOptionPane.YES_NO_OPTION,
                messageType = JOptionPane.QUESTION_MESSAGE,
                options = arrayOf(
                    I18n.getString("termora.welcome.contextmenu.import"),
                    I18n.getString("termora.welcome.contextmenu.download")
                ),
                initialValue = I18n.getString("termora.welcome.contextmenu.import")
            )
            if (code == JOptionPane.DEFAULT_OPTION) {
                return
            } else if (code != JOptionPane.YES_OPTION) {
                chooser.setSelectedFile(File("termora_import.csv"))
                if (chooser.showSaveDialog(owner) == JFileChooser.APPROVE_OPTION) {
                    CSVPrinter(
                        FileWriter(chooser.selectedFile, Charsets.UTF_8),
                        CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()
                    ).use { printer ->
                        printer.printRecord(
                            "Projects/Dev",
                            "Web Server",
                            "192.168.1.1",
                            "22",
                            "root",
                            SSHProtocolProvider.PROTOCOL
                        )
                        printer.printRecord(
                            "Projects/Prod",
                            "Web Server",
                            "serverhost.com",
                            "2222",
                            "root",
                            SSHProtocolProvider.PROTOCOL
                        )
                        printer.printRecord(
                            StringUtils.EMPTY,
                            "Web Server",
                            "serverhost.com",
                            "2222",
                            "user",
                            SSHProtocolProvider.PROTOCOL
                        )
                    }
                    OptionPane.openFileInFolder(
                        owner,
                        chooser.selectedFile,
                        I18n.getString("termora.welcome.contextmenu.import.csv.download-template-done-open-folder"),
                        I18n.getString("termora.welcome.contextmenu.import.csv.download-template-done")
                    )
                }
                return
            }
        }

        // 选择文件
        if (type != ImportType.SSH) {
            val code = chooser.showOpenDialog(owner)
            if (code != JFileChooser.APPROVE_OPTION) {
                return
            }
        }

        val file = chooser.selectedFile
        if (file != null && file.parentFile != null) {
            properties.putString(
                "NewHostTree.ImportHosts.defaultDir",
                (if (FileUtils.isDirectory(file)) file else file.parentFile).absolutePath
            )
        }

        val nodes = when (type) {
            ImportType.SSH -> parseFromSSH(folder)
            ImportType.WindTerm -> parseFromWindTerm(folder, file)
            ImportType.SecureCRT -> parseFromSecureCRT(folder, file)
            ImportType.MobaXterm -> parseFromMobaXterm(folder, file)
            ImportType.PuTTY -> parseFromPuTTY(folder, file)
            ImportType.Xshell -> parseFromXshell(folder, file)
            ImportType.FinalShell -> parseFromFinalShell(folder, file)
            ImportType.electerm -> parseFromElecterm(folder, file)
            ImportType.CSV -> file.bufferedReader().use { parseFromCSV(folder, it) }
        }

        if (nodes.isEmpty()) return

        val hostManager = HostManager.getInstance()
        for (node in nodes) {
            hostManager.addHost(
                node.host.copy(
                    ownerType = folder.host.ownerType,
                    ownerId = folder.host.ownerId,
                ),
                DatabaseChangedExtension.Source.Sync
            )
            for (host in node.getAllChildren().map { it.host }) {
                hostManager.addHost(
                    host.copy(ownerType = folder.host.ownerType, ownerId = folder.host.ownerId),
                    DatabaseChangedExtension.Source.Sync
                )
            }
        }

        // 重新加载
        model.reload(folder)

        // expand root
        expandPath(TreePath(model.getPathToRoot(folder)))
    }

    private fun parseFromWindTerm(folder: HostTreeNode, file: File): List<HostTreeNode> {
        val sessions = ohMyJson.runCatching { ohMyJson.parseToJsonElement(file.readText()).jsonArray }
            .onFailure { OptionPane.showMessageDialog(owner, ExceptionUtils.getMessage(it)) }
            .getOrNull() ?: return emptyList()

        val sw = StringWriter()
        CSVPrinter(sw, CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()).use { printer ->
            for (i in 0 until sessions.size) {
                val json = sessions[i].jsonObject
                val protocol = json["session.protocol"]?.jsonPrimitive?.content ?: SSHProtocolProvider.PROTOCOL
                if (!StringUtils.equalsIgnoreCase(SSHProtocolProvider.PROTOCOL, protocol)) continue
                val label = json["session.label"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                val target = json["session.target"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                val port = json["session.port"]?.jsonPrimitive?.intOrNull ?: 22
                val group = json["session.group"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                val groups = group.split(">")
                printer.printRecord(
                    groups.joinToString("/"),
                    label,
                    target,
                    port,
                    StringUtils.EMPTY,
                    SSHProtocolProvider.PROTOCOL
                )
            }
        }

        return parseFromCSV(folder, StringReader(sw.toString()))
    }

    private fun parseFromSSH(folder: HostTreeNode): List<HostTreeNode> {
        val entries = HostConfigEntry.readHostConfigEntries(HostConfigEntry.getDefaultHostConfigFile())

        val sw = StringWriter()
        CSVPrinter(sw, CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()).use { printer ->
            for (entry in entries) {
                printer.printRecord(
                    StringUtils.EMPTY,
                    StringUtils.defaultString(entry.host),
                    StringUtils.defaultString(entry.hostName),
                    if (entry.port == 0) 22 else entry.port,
                    StringUtils.defaultString(entry.username),
                    SSHProtocolProvider.PROTOCOL
                )
            }
        }

        return parseFromCSV(folder, StringReader(sw.toString()))
    }

    private fun parseFromSecureCRT(folder: HostTreeNode, file: File): List<HostTreeNode> {
        val xPath = XPathFactory.newInstance().newXPath()
        val db = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = db.parse(file)
        val sessionElement = xPath.compile("/VanDyke/key[@name='Sessions']")
            .evaluate(doc, XPathConstants.NODE) as Element? ?: return emptyList()
        val nodeList = xPath.compile(".//key[not(key)]").evaluate(sessionElement, XPathConstants.NODESET) as NodeList
        if (nodeList.length == 0) return emptyList()

        val sw = StringWriter()
        CSVPrinter(sw, CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()).use { printer ->
            for (i in 0 until nodeList.length) {
                val ele = nodeList.item(i) as Element
                val protocol = xPath.compile("./string[@name='Protocol Name']/text()").evaluate(ele)
                if (!StringUtils.equalsIgnoreCase(protocol, "SSH2")) continue
                val label = ele.getAttribute("name")
                if (StringUtils.isBlank(label)) continue
                val hostname = xPath.compile("./string[@name='Hostname']/text()").evaluate(ele)
                if (StringUtils.isBlank(hostname)) continue
                val username = xPath.compile("./string[@name='Username']/text()").evaluate(ele)
                val port = xPath.compile("./dword[@name='[SSH2] Port']/text()").evaluate(ele)?.toIntOrNull() ?: 22


                val folders = mutableListOf<String>()
                var p = ele.parentNode as Element
                while (p != sessionElement) {
                    folders.addFirst(p.getAttribute("name"))
                    p = p.parentNode as Element
                }
                printer.printRecord(
                    folders.joinToString("/"),
                    label,
                    hostname,
                    port.toString(),
                    username,
                    SSHProtocolProvider.PROTOCOL
                )
            }
        }

        return parseFromCSV(folder, StringReader(sw.toString()))
    }

    private fun parseFromPuTTY(folder: HostTreeNode, file: File): List<HostTreeNode> {
        val reg = Reg(file)
        val prefix = "HKEY_CURRENT_USER\\Software\\SimonTatham\\PuTTY\\Sessions\\"


        val sw = StringWriter()
        CSVPrinter(sw, CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()).use { printer ->
            for (key in reg.keys) {
                if (!key.startsWith(prefix)) {
                    continue
                }
                val properties = reg[key]?.toProperties() ?: continue
                val label = StringUtils.removeStart(key, prefix)
                val hostname = properties.getProperty("HostName")
                val username = properties.getProperty("UserName")
                val port = properties.getProperty("PortNumber")
                printer.printRecord(
                    StringUtils.EMPTY,
                    label,
                    hostname,
                    port.toString(),
                    username,
                    SSHProtocolProvider.PROTOCOL
                )
            }
        }

        return parseFromCSV(folder, StringReader(sw.toString()))
    }

    private fun parseFromMobaXterm(folder: HostTreeNode, file: File): List<HostTreeNode> {
        val ini = Ini()
        ini.config.isEscapeKeyOnly = true
        ini.load(file)

        val bookmarks = mutableListOf<String>()
        for (key in ini.keys) {
            if (key.startsWith("Bookmarks")) {
                bookmarks.add(key)
            }
        }

        val sw = StringWriter()
        CSVPrinter(sw, CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()).use { printer ->

            for (bookmark in bookmarks) {
                val properties = (ini[bookmark] ?: continue).toProperties()
                // 删除不必要元素
                properties.remove("ImgNum")
                val folders = FilenameUtils.separatorsToUnix(
                    (properties.remove("SubRep")
                        ?: StringUtils.EMPTY).toString()
                )

                for (key in properties.stringPropertyNames()) {
                    val segments = properties.getProperty(key).split("%")
                    if (segments.isEmpty()) continue
                    // ssh: #109#0
                    // telnet: #98#1
                    if (segments.first() != "#109#0") continue
                    val hostname = segments.getOrNull(1) ?: StringUtils.EMPTY
                    val port = segments.getOrNull(2) ?: 22
                    printer.printRecord(folders, key, hostname, port, StringUtils.EMPTY, SSHProtocolProvider.PROTOCOL)
                }
            }
        }

        return parseFromCSV(folder, StringReader(sw.toString()))
    }

    private fun parseFromXshell(folder: HostTreeNode, dir: File): List<HostTreeNode> {
        val files = FileUtils.listFiles(dir, arrayOf("xsh"), true)
        if (files.isEmpty()) {
            OptionPane.showMessageDialog(
                owner,
                I18n.getString("termora.welcome.contextmenu.import.xshell-folder-empty")
            )
            return emptyList()
        }

        val sw = StringWriter()
        CSVPrinter(sw, CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()).use { printer ->
            for (file in files) {
                val ini = Ini(file)
                val protocol = ini.get("CONNECTION", "Protocol") ?: SSHProtocolProvider.PROTOCOL
                if (!StringUtils.equalsIgnoreCase(SSHProtocolProvider.PROTOCOL, protocol)) continue
                val folders = FilenameUtils.separatorsToUnix(file.parentFile.relativeTo(dir).toString())
                val hostname = ini.get("CONNECTION", "Host") ?: StringUtils.EMPTY
                val label = file.nameWithoutExtension
                val port = ini.get("CONNECTION", "Port")?.toIntOrNull() ?: 22
                val username = ini.get("CONNECTION:AUTHENTICATION", "UserName") ?: StringUtils.EMPTY
                printer.printRecord(folders, label, hostname, port, username, SSHProtocolProvider.PROTOCOL)
            }
        }

        return parseFromCSV(folder, StringReader(sw.toString()))
    }

    private fun parseFromFinalShell(folder: HostTreeNode, dir: File): List<HostTreeNode> {
        val files = FileUtils.listFiles(
            dir,
            FileFilterUtils.suffixFileFilter("_connect_config.json"),
            FileFilterUtils.trueFileFilter()
        )

        if (files.isEmpty()) {
            OptionPane.showMessageDialog(
                owner,
                I18n.getString("termora.welcome.contextmenu.import.finalshell-folder-empty")
            )
            return emptyList()
        }

        val sw = StringWriter()
        CSVPrinter(sw, CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()).use { printer ->
            for (file in files) {
                try {
                    val json = ohMyJson.runCatching { ohMyJson.parseToJsonElement(file.readText()) }
                        .getOrNull()?.jsonObject ?: continue
                    val username = json["user_name"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                    val label = json["name"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                    val host = json["host"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                    val port = json["port"]?.jsonPrimitive?.intOrNull ?: 22
                    if (StringUtils.isAllBlank(host, label)) continue
                    val folders = FilenameUtils.separatorsToUnix(file.parentFile.relativeTo(dir).toString())
                    printer.printRecord(
                        folders,
                        StringUtils.defaultIfBlank(label, host),
                        host,
                        port,
                        username,
                        SSHProtocolProvider.PROTOCOL
                    )
                } catch (e: Exception) {
                    if (log.isErrorEnabled) {
                        log.error(file.absolutePath, e)
                    }
                }
            }
        }

        return parseFromCSV(folder, StringReader(sw.toString()))
    }

    @Serializable
    private data class ElectermGroup(
        val id: String = StringUtils.EMPTY,
        val title: String = StringUtils.EMPTY,
        val bookmarkIds: Set<String> = emptySet(),
        val bookmarkGroupIds: Set<String> = emptySet(),
    )

    private fun parseFromElecterm(folder: HostTreeNode, file: File): List<HostTreeNode> {
        val json = ohMyJson.parseToJsonElement(file.readText()).jsonObject
        val bookmarks = json["bookmarks"]?.jsonArray ?: return emptyList()
        val bookmarkGroups = ohMyJson.decodeFromJsonElement<List<ElectermGroup>>(
            json["bookmarkGroups"]?.jsonArray ?: JsonArray(emptyList())
        )


        val sw = StringWriter()
        CSVPrinter(sw, CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()).use { printer ->
            for (i in 0 until bookmarks.size) {
                val host = bookmarks[i].jsonObject
                val type = host["type"]?.jsonPrimitive?.content ?: SSHProtocolProvider.PROTOCOL
                if (!StringUtils.equalsIgnoreCase(type, SSHProtocolProvider.PROTOCOL)) continue
                val hostname = host["host"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                val id = host["id"]?.jsonPrimitive?.content ?: continue
                val title = host["title"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                if (StringUtils.isAllBlank(title, hostname)) continue
                val username = host["username"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                val port = host["port"]?.jsonPrimitive?.intOrNull ?: 22

                val folderNames = mutableListOf<String>()
                var group = bookmarkGroups.find { it.bookmarkIds.contains(id) }
                while (group != null && group.id != "default") {
                    folderNames.addFirst(group.title)
                    group = bookmarkGroups.find { it.bookmarkGroupIds.contains(group?.id ?: StringUtils.EMPTY) }
                }

                printer.printRecord(
                    folderNames.joinToString("/"),
                    StringUtils.defaultIfBlank(title, hostname),
                    hostname,
                    port,
                    username,
                    SSHProtocolProvider.PROTOCOL
                )
            }

        }

        return parseFromCSV(folder, StringReader(sw.toString()))
    }

    private fun parseFromCSV(folderNode: HostTreeNode, sr: Reader): List<HostTreeNode> {
        val records = CSVParser.builder()
            .setFormat(CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).setSkipHeaderRecord(true).get())
            .setCharset(Charsets.UTF_8)
            .setReader(sr)
            .get()
            .use { it.records }
        // 把现有目录提取出来，避免重复创建
        val nodes = folderNode.clone(setOf("Folder"))
            .childrenNode().filter { it.isFolder }
            .toMutableList()

        for (record in records) {
            val map = mutableMapOf<String, String>()
            for (e in record.parser.headerMap.keys) {
                map[e] = record.get(e)
            }

            val folder = map["Folders"] ?: StringUtils.EMPTY
            val label = map["Label"] ?: StringUtils.EMPTY
            val hostname = map["Hostname"] ?: StringUtils.EMPTY
            val port = map["Port"]?.toIntOrNull() ?: 22
            val username = map["Username"] ?: StringUtils.EMPTY
            val protocol = map["Protocol"] ?: "SSH"
            // 仅支持 SSH、RDP 协议
            if (StringUtils.equalsAnyIgnoreCase(protocol, "SSH", "RDP").not()) continue
            if (StringUtils.isAllBlank(hostname, label)) continue

            var p: HostTreeNode? = null
            if (folder.isNotBlank()) {
                for ((j, name) in folder.split("/").withIndex()) {
                    val folders = if (j == 0 || p == null) nodes
                    else p.children().toList().filterIsInstance<HostTreeNode>()
                    val n = HostTreeNode(
                        Host(
                            name = name, protocol = "Folder",
                            parentId = p?.host?.id ?: folderNode.id
                        )
                    )
                    val cp = folders.find { it.isFolder && it.host.name == name }
                    if (cp != null) {
                        p = cp
                        continue
                    }
                    if (p == null) {
                        p = n
                        nodes.add(n)
                    } else {
                        p.add(n)
                        p = n
                    }
                }
            }

            val n = HostTreeNode(
                Host(
                    name = StringUtils.defaultIfBlank(label, hostname),
                    host = hostname,
                    port = port,
                    username = username,
                    protocol = protocol,
                    parentId = p?.host?.id ?: folderNode.id,
                )
            )

            if (p == null) {
                nodes.add(n)
            } else {
                p.add(n)
            }
        }

        return nodes
    }


    private enum class ImportType {
        WindTerm,
        CSV,
        Xshell,
        PuTTY,
        SecureCRT,
        MobaXterm,
        SSH,
        FinalShell,
        electerm,
    }


}