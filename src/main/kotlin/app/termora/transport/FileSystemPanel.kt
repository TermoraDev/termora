package app.termora.transport

import app.termora.*
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatToolBar
import com.formdev.flatlaf.icons.FlatFileViewDirectoryIcon
import com.formdev.flatlaf.icons.FlatFileViewFileIcon
import com.formdev.flatlaf.util.SystemInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.sshd.sftp.client.SftpClient
import org.apache.sshd.sftp.client.fs.SftpFileSystem
import org.apache.sshd.sftp.client.fs.SftpPath
import org.jdesktop.swingx.JXBusyLabel
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Desktop
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDropEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.nio.file.*
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import kotlin.io.path.exists
import kotlin.io.path.isDirectory


/**
 * 文件系统面板
 */
class FileSystemPanel(
    private val fileSystem: FileSystem,
    private val transportManager: TransportManager,
    private val host: Host
) : JPanel(BorderLayout()), Disposable,
    FileSystemTransportListener.Provider {

    companion object {
        private val log = LoggerFactory.getLogger(FileSystemPanel::class.java)
    }

    private val tableModel = FileSystemTableModel(fileSystem)
    private val table = JTable(tableModel)
    private val parentBtn = JButton(Icons.up)
    private val workdirTextField = OutlineTextField()
    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val layeredPane = FileSystemLayeredPane()
    private val loadingPanel = LoadingPanel()
    private val bookmarkBtn = BookmarkButton()
    private val homeBtn = JButton(Icons.homeFolder)

    val workdir get() = tableModel.workdir

    init {
        initView()
        initEvents()
    }

    private fun initView() {

        // 设置书签名称
        bookmarkBtn.name = "Host.${host.id}.Bookmarks"
        bookmarkBtn.isBookmark = bookmarkBtn.getBookmarks().contains(workdir.toString())

        table.autoResizeMode = JTable.AUTO_RESIZE_OFF
        table.fillsViewportHeight = true
        table.putClientProperty(
            FlatClientProperties.STYLE, mapOf(
                "showHorizontalLines" to true,
                "showVerticalLines" to true,
            )
        )

        table.setDefaultRenderer(
            Any::class.java,
            DefaultTableCellRenderer().apply {
                horizontalAlignment = SwingConstants.CENTER
            }
        )

        val modifyDateColumn = table.columnModel.getColumn(FileSystemTableModel.COLUMN_LAST_MODIFIED_TIME)
        modifyDateColumn.preferredWidth = 130

        val nameColumn = table.columnModel.getColumn(FileSystemTableModel.COLUMN_NAME)
        nameColumn.preferredWidth = 250
        nameColumn.setCellRenderer(object : DefaultTableCellRenderer() {
            private val b = BorderFactory.createEmptyBorder(0, 4, 0, 0)
            private val d = FlatFileViewDirectoryIcon()
            private val f = FlatFileViewFileIcon()

            override fun getTableCellRendererComponent(
                table: JTable?,
                value: Any,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                var text = value.toString()
                // name
                if (value is FileSystemTableModel.CacheablePath) {
                    text = value.fileName
                    icon = if (value.isDirectory) d else f
                    iconTextGap = 4
                }

                val c = super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column)
                border = b
                return c
            }
        })

        parentBtn.toolTipText = I18n.getString("termora.transport.parent-folder")


        val toolbar = FlatToolBar()
        toolbar.add(homeBtn)
        toolbar.add(Box.createHorizontalStrut(2))
        toolbar.add(workdirTextField)
        toolbar.add(bookmarkBtn)
        toolbar.add(parentBtn)
        toolbar.add(JButton(Icons.refresh).apply {
            addActionListener { reload() }
            toolTipText = I18n.getString("termora.transport.table.contextmenu.refresh")
        })
        toolbar.border = BorderFactory.createEmptyBorder(4, 2, 4, 2)

        val scrollPane = JScrollPane(table)
        scrollPane.border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor)
        layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER as Any)
        layeredPane.add(loadingPanel, JLayeredPane.MODAL_LAYER as Any)

        add(toolbar, BorderLayout.NORTH)
        add(layeredPane, BorderLayout.CENTER)

    }

    private fun initEvents() {

        homeBtn.addActionListener {
            if (tableModel.isLocalFileSystem) {
                tableModel.workdir(SystemUtils.USER_HOME)
            } else if (fileSystem is SftpFileSystem) {
                tableModel.workdir(fileSystem.defaultDir)
            }
            reload()
        }

        bookmarkBtn.addActionListener { e ->
            if (e.actionCommand.isNullOrBlank()) {
                if (bookmarkBtn.isBookmark) {
                    bookmarkBtn.deleteBookmark(workdir.toString())
                } else {
                    bookmarkBtn.addBookmark(workdir.toString())
                }
                bookmarkBtn.isBookmark = !bookmarkBtn.isBookmark
            } else if (!loadingPanel.isLoading) {
                tableModel.workdir(e.actionCommand)
                reload()
            }
        }

        // contextmenu
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val r = table.rowAtPoint(e.point)
                    if (r >= 0 && r < table.rowCount) {
                        if (!table.isRowSelected(r)) {
                            table.setRowSelectionInterval(r, r)
                        }
                    } else {
                        table.clearSelection()
                    }

                    val rows = table.selectedRows

                    if (!table.hasFocus()) {
                        table.requestFocusInWindow()
                    }

                    showContextMenu(rows.filter { it != 0 }.toIntArray(), e)
                }
            }
        })


        // double click
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount % 2 == 0) {
                    val row = table.selectedRow
                    if (row < 0) return
                    val path = tableModel.getCacheablePath(row)
                    if (path.isDirectory) {
                        openFolder()
                    } else {
                        transport(listOf(path))
                    }
                }
            }
        })

        // 本地文件系统不支持本地拖拽进去
        if (!tableModel.isLocalFileSystem) {
            table.dropTarget = object : DropTarget() {
                override fun drop(dtde: DropTargetDropEvent) {
                    val transportPanel = getTransportPanel() ?: return
                    val localFileSystemPanel = transportPanel.leftFileSystemTabbed.getFileSystemPanel(0) ?: return

                    dtde.acceptDrop(DnDConstants.ACTION_COPY)
                    val files = dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
                    if (files.isEmpty()) return

                    val paths = files.filterIsInstance<File>().map { FileSystemTableModel.CacheablePath(it.toPath()) }
                    for (path in paths) {
                        if (path.isDirectory) {
                            Files.walk(path.path).use {
                                for (e in it) {
                                    transportPanel.transport(
                                        sourceWorkdir = path.path.parent,
                                        targetWorkdir = workdir,
                                        isSourceDirectory = e.isDirectory(),
                                        sourcePath = e,
                                        sourceHolder = localFileSystemPanel,
                                        targetHolder = this@FileSystemPanel
                                    )
                                }
                            }
                        } else {
                            transportPanel.transport(
                                sourceWorkdir = localFileSystemPanel.workdir,
                                targetWorkdir = workdir,
                                isSourceDirectory = false,
                                sourcePath = path.path,
                                sourceHolder = localFileSystemPanel,
                                targetHolder = this@FileSystemPanel
                            )
                        }
                    }
                }
            }.apply {
                this.defaultActions = DnDConstants.ACTION_COPY
            }
        }

        // 工作目录变动
        tableModel.addPropertyChangeListener {
            if (it.propertyName == "workdir") {
                workdirTextField.text = tableModel.workdir.toAbsolutePath().toString()
                bookmarkBtn.isBookmark = bookmarkBtn.getBookmarks().contains(workdirTextField.text)
            }
        }

        // 修改工作目录
        workdirTextField.addActionListener {
            val text = workdirTextField.text
            if (text.isBlank()) {
                workdirTextField.text = tableModel.workdir.toAbsolutePath().toString()
                reload()
            } else {
                val path = fileSystem.getPath(workdirTextField.text)
                if (Files.exists(path)) {
                    tableModel.workdir(path)
                    reload()
                } else {
                    workdirTextField.outline = "error"
                }
            }
        }

        // 返回上一级目录
        parentBtn.addActionListener {
            if (tableModel.rowCount > 0) {
                val path = tableModel.getCacheablePath(0)
                if (path.isDirectory && path.fileName == "..") {
                    tableModel.workdir(path.path)
                    reload()
                }
            }
        }


    }


    @OptIn(DelicateCoroutinesApi::class)
    fun reload() {
        if (loadingPanel.isLoading) {
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            runCatching { suspendReload() }
        }
    }

    private suspend fun suspendReload() {
        if (loadingPanel.isLoading) {
            return
        }

        withContext(Dispatchers.Swing) {
            // reload
            loadingPanel.start()
            workdirTextField.text = workdir.toString()
        }

        try {
            tableModel.reload()
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
            withContext(Dispatchers.Swing) {
                OptionPane.showMessageDialog(
                    owner,
                    ExceptionUtils.getRootCauseMessage(e),
                    messageType = JOptionPane.ERROR_MESSAGE
                )
            }
            return
        } finally {
            withContext(Dispatchers.Swing) {
                loadingPanel.stop()
            }
        }

        withContext(Dispatchers.Swing) {
            table.scrollRectToVisible(table.getCellRect(0, 0, true))
        }
    }


    override fun addFileSystemTransportListener(listener: FileSystemTransportListener) {
        listenerList.add(FileSystemTransportListener::class.java, listener)
    }

    override fun removeFileSystemTransportListener(listener: FileSystemTransportListener) {
        listenerList.remove(FileSystemTransportListener::class.java, listener)
    }

    private fun openFolder() {
        val row = table.selectedRow
        if (row < 0) return
        val path = tableModel.getCacheablePath(row)
        if (path.isDirectory) {
            tableModel.workdir(path.path)
            reload()
        }
    }

    private fun canTransfer(): Boolean {
        return getTransportPanel()?.getTargetFileSystemPanel(this) != null
    }


    private fun getTransportPanel(): TransportPanel? {
        var p = this as Component?
        while (p != null) {
            if (p is TransportPanel) {
                return p
            }
            p = p.parent
        }
        return null
    }

    private fun showContextMenu(rows: IntArray, event: MouseEvent) {
        val popupMenu = FlatPopupMenu()
        val newMenu = JMenu(I18n.getString("termora.transport.table.contextmenu.new"))

        // 创建文件夹
        newMenu.add(I18n.getString("termora.transport.table.contextmenu.new.folder")).addActionListener {
            newFolderOrFile(file = false)
        }

        // 创建文件
        newMenu.add(I18n.getString("termora.transport.table.contextmenu.new.file")).addActionListener {
            newFolderOrFile(file = true)
        }


        // 传输
        val transfer = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.transfer"))
        transfer.addActionListener {
            val paths = rows.filter { it != 0 }.map { tableModel.getCacheablePath(it) }
            if (paths.isNotEmpty()) {
                transport(paths)
            }
        }
        popupMenu.addSeparator()

        // 复制路径
        val copyPath = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.copy-path"))
        copyPath.addActionListener {
            val row = table.selectedRow
            if (row > 0) {
                toolkit.systemClipboard.setContents(
                    StringSelection(
                        tableModel.getPath(row).toAbsolutePath().toString()
                    ), null
                )
            }
        }

        // 如果是本地，那么支持打开本地路径
        if (tableModel.isLocalFileSystem) {
            if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                popupMenu.add(
                    I18n.getString(
                        "termora.transport.table.contextmenu.open-in-folder",
                        if (SystemInfo.isMacOS) I18n.getString("termora.finder")
                        else if (SystemInfo.isWindows) I18n.getString("termora.explorer")
                        else I18n.getString("termora.folder")
                    )
                ).addActionListener {
                    val row = table.selectedRow
                    if (row > 0) {
                        Desktop.getDesktop().browseFileDirectory(tableModel.getPath(row).toFile())
                    }
                }
            }

        }
        popupMenu.addSeparator()

        // 重命名
        val rename = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.rename"))
        rename.addActionListener { renamePath(tableModel.getPath(rows.last())) }

        // 删除
        val delete = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.delete")).apply {
            addActionListener { deletePaths(rows) }
        }

        // rm -rf
        val rmrf = popupMenu.add(JMenuItem("rm -rf", Icons.errorIntroduction)).apply {
            addActionListener {
                deletePaths(rows, true)
            }
        }

        // 只有 SFTP 可以
        if (fileSystem !is SftpFileSystem) {
            rmrf.isVisible = false
        }

        // 修改权限
        val permission = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.change-permissions"))
        permission.isEnabled = false

        // 如果是本地系统文件，那么不允许修改权限，用户应该自己修改
        if (!tableModel.isLocalFileSystem && rows.isNotEmpty()) {
            permission.isEnabled = true
            permission.addActionListener { changePermissions(tableModel.getCacheablePath(rows.last())) }
        }
        popupMenu.addSeparator()

        // 刷新
        popupMenu.add(I18n.getString("termora.transport.table.contextmenu.refresh"))
            .apply { addActionListener { reload() } }
        popupMenu.addSeparator()

        // 新建
        popupMenu.add(newMenu)


        if (rows.isEmpty()) {
            transfer.isEnabled = false
            rename.isEnabled = false
            delete.isEnabled = false
            rmrf.isEnabled = false
            copyPath.isEnabled = false
            permission.isEnabled = false
        } else {
            transfer.isEnabled = canTransfer()
        }


        popupMenu.show(table, event.x, event.y)
    }


    @OptIn(DelicateCoroutinesApi::class)
    private fun renamePath(path: Path) {
        val fileName = path.fileName.toString()
        val text = InputDialog(
            owner = owner,
            title = fileName,
            text = fileName,
        ).getText() ?: return

        if (fileName == text) return

        loadingPanel.stop()

        GlobalScope.launch(Dispatchers.IO) {
            val result = runCatching {
                Files.move(path, path.parent.resolve(text), StandardCopyOption.ATOMIC_MOVE)
            }.onFailure {
                withContext(Dispatchers.Swing) {
                    OptionPane.showMessageDialog(
                        owner, it.message ?: ExceptionUtils.getRootCauseMessage(it),
                        messageType = JOptionPane.ERROR_MESSAGE
                    )
                }
            }

            withContext(Dispatchers.Swing) {
                loadingPanel.stop()
            }

            if (result.isSuccess) {
                reload()
            }
        }

    }


    @OptIn(DelicateCoroutinesApi::class)
    private fun newFolderOrFile(file: Boolean = false) {
        val title = I18n.getString("termora.transport.table.contextmenu.new.${if (file) "file" else "folder"}")
        val text = InputDialog(
            owner = owner,
            title = title,
        ).getText() ?: return

        if (text.isEmpty()) return

        loadingPanel.stop()

        GlobalScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val path = workdir.resolve(text)
                if (path.exists()) {
                    throw IllegalStateException(I18n.getString("termora.transport.file-already-exists", text))
                }
                if (file)
                    Files.createFile(path)
                else
                    Files.createDirectories(path)
            }.onFailure {
                withContext(Dispatchers.Swing) {
                    OptionPane.showMessageDialog(
                        owner, it.message ?: ExceptionUtils.getRootCauseMessage(it),
                        messageType = JOptionPane.ERROR_MESSAGE
                    )
                }
            }

            withContext(Dispatchers.Swing) {
                loadingPanel.stop()
            }

            if (result.isSuccess) {
                reload()
            }
        }

    }


    @OptIn(DelicateCoroutinesApi::class)
    private fun deletePaths(rows: IntArray, rm: Boolean = false) {
        if (OptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(this),
                I18n.getString(if (rm) "termora.transport.table.contextmenu.rm-warning" else "termora.transport.table.contextmenu.delete-warning"),
                messageType = if (rm) JOptionPane.ERROR_MESSAGE else JOptionPane.WARNING_MESSAGE
            ) != JOptionPane.YES_OPTION
        ) {
            return
        }

        loadingPanel.start()

        GlobalScope.launch(Dispatchers.IO) {
            runCatching {
                for (row in rows.sortedDescending()) {
                    try {
                        deleteRecursively(tableModel.getPath(row), rm)
                        withContext(Dispatchers.Swing) {
                            tableModel.removeRow(row)
                        }
                    } catch (e: Exception) {
                        if (log.isErrorEnabled) {
                            log.error(e.message, e)
                        }
                    }
                }
            }.onFailure {
                if (log.isErrorEnabled) {
                    log.error(it.message, it)
                }
            }

            withContext(Dispatchers.Swing) {
                loadingPanel.stop()
            }
        }
    }

    private fun deleteRecursively(path: Path, rm: Boolean) {
        if (path.fileSystem == FileSystems.getDefault()) {
            FileUtils.deleteDirectory(path.toFile())
        } else if (path.fileSystem is SftpFileSystem) {
            val fs = path.fileSystem as SftpFileSystem
            if (rm) {
                fs.session.executeRemoteCommand("rm -rf '$path'")
            } else {
                fs.client.use {
                    deleteRecursivelySFTP(path as SftpPath, it)
                }
            }
        }
    }

    /**
     * 优化删除效率，采用一个连接
     */
    private fun deleteRecursivelySFTP(path: SftpPath, sftpClient: SftpClient) {
        val isDirectory = if (path.attributes != null) path.attributes.isDirectory else path.isDirectory()
        if (isDirectory) {
            for (e in sftpClient.readDir(path.toString())) {
                if (e.filename == ".." || e.filename == ".") {
                    continue
                }
                if (e.attributes.isDirectory) {
                    deleteRecursivelySFTP(path.resolve(e.filename), sftpClient)
                } else {
                    sftpClient.remove(path.resolve(e.filename).toString())
                }
            }
            sftpClient.rmdir(path.toString())
        } else {
            sftpClient.remove(path.toString())
        }

    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun changePermissions(cacheablePath: FileSystemTableModel.CacheablePath) {
        val dialog = PosixFilePermissionDialog(
            SwingUtilities.getWindowAncestor(this),
            cacheablePath.posixFilePermissions
        )
        val permissions = dialog.open() ?: return

        loadingPanel.start()

        GlobalScope.launch(Dispatchers.IO) {
            val result = runCatching {
                Files.setPosixFilePermissions(cacheablePath.path, permissions)
            }

            result.onFailure {
                if (log.isErrorEnabled) {
                    log.error(it.message, it)
                }
                withContext(Dispatchers.Swing) {
                    OptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(this@FileSystemPanel), ExceptionUtils.getRootCauseMessage(it),
                        messageType = JOptionPane.ERROR_MESSAGE
                    )
                }
            }

            withContext(Dispatchers.Swing) {
                loadingPanel.stop()
            }

            if (result.isSuccess) {
                reload()
            }


        }
    }

    private fun transport(paths: List<FileSystemTableModel.CacheablePath>) {
        assertEventDispatchThread()
        if (!canTransfer()) {
            return
        }

        loadingPanel.start()
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { doTransport(paths) }
            withContext(Dispatchers.Swing) {
                loadingPanel.stop()
            }
        }
    }

    private suspend fun doTransport(paths: List<FileSystemTableModel.CacheablePath>) {
        if (paths.isEmpty()) return

        val listeners = listenerList.getListeners(FileSystemTransportListener::class.java)
        if (listeners.isEmpty()) return


        // 收集数据
        for (e in paths) {

            if (!e.isDirectory) {
                withContext(Dispatchers.Swing) {
                    listeners.forEach { it.transport(this@FileSystemPanel, workdir, false, e.path) }
                }
                continue
            }

            withContext(Dispatchers.IO) {
                Files.walk(e.path).use { walkPaths ->
                    for (path in walkPaths) {
                        if (path is SftpPath) {
                            val isDirectory = if (path.attributes != null)
                                path.attributes.isDirectory else path.isDirectory()
                            withContext(Dispatchers.Swing) {
                                listeners.forEach { it.transport(this@FileSystemPanel, workdir, isDirectory, path) }
                            }
                        } else {
                            val isDirectory = path.isDirectory()
                            withContext(Dispatchers.Swing) {
                                listeners.forEach { it.transport(this@FileSystemPanel, workdir, isDirectory, path) }
                            }
                        }
                    }
                }
            }
        }
    }

    private class LoadingPanel : JPanel() {
        private val busyLabel = JXBusyLabel()

        val isLoading get() = busyLabel.isBusy

        init {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(50, 0, 0, 0)

            add(busyLabel, BorderLayout.CENTER)
            addMouseListener(object : MouseAdapter() {})
            isVisible = false
        }

        fun start() {
            busyLabel.isBusy = true
            isVisible = true
        }

        fun stop() {
            busyLabel.isBusy = false
            isVisible = false
        }
    }

    private class FileSystemLayeredPane : JLayeredPane() {
        override fun doLayout() {
            synchronized(treeLock) {
                val w = width
                val h = height
                for (c in components) {
                    if (c is JScrollPane) {
                        c.setBounds(0, 0, w, h)
                    } else if (c is LoadingPanel) {
                        c.setBounds(0, 0, w, h)
                    }
                }
            }
        }
    }


}