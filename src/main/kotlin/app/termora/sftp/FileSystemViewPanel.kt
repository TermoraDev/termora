package app.termora.sftp

import app.termora.*
import app.termora.actions.DataProvider
import app.termora.database.DatabaseManager
import app.termora.terminal.DataKey
import app.termora.vfs2.sftp.MySftpFileSystem
import com.formdev.flatlaf.extras.components.FlatToolBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.FileSystem
import org.apache.commons.vfs2.VFS
import org.apache.commons.vfs2.provider.local.LocalFileSystem
import org.jdesktop.swingx.JXBusyLabel
import java.awt.BorderLayout
import java.awt.event.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import javax.swing.*

class FileSystemViewPanel(
    val host: Host,
    private val homeDirectory: FileObject,
    private val transportManager: TransportManager,
    private val coroutineScope: CoroutineScope,
) : JPanel(BorderLayout()), Disposable, DataProvider, FileSystemProvider {

    private var fileSystem: FileSystem = homeDirectory.fileSystem
    private val properties get() = DatabaseManager.getInstance().properties
    private val table = FileSystemViewTable(this, transportManager, coroutineScope)
    private val disposed = AtomicBoolean(false)
    private var nextReloadTicks = emptyArray<Consumer<Unit>>()
    private val isLoading = AtomicBoolean(false)
    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val loadingPanel = LoadingPanel()
    private val layeredPane = LayeredPane()
    private val nav = FileSystemViewNav(this, homeDirectory)
    private var workdir = homeDirectory
    private val model get() = table.model as FileSystemViewTableModel
    private val showHiddenFilesKey = "termora.transport.host.${host.id}.show-hidden-files"
    private var useFileHiding: Boolean
        get() = properties.getString(showHiddenFilesKey, "true").toBoolean()
        set(value) = properties.putString(showHiddenFilesKey, value.toString())

    val isDisposed get() = disposed.get()

    init {
        initViews()
        initEvents()
    }

    private fun initViews() {

        val toolbar = FlatToolBar()
        toolbar.add(createHomeFolderButton())
        toolbar.add(Box.createHorizontalStrut(2))
        toolbar.add(nav)
        toolbar.add(createBookmarkButton())
        toolbar.add(createParentFolderButton())
        toolbar.add(createHiddenFilesButton())
        toolbar.add(createRefreshButton())

        add(toolbar, BorderLayout.NORTH)
        add(layeredPane, BorderLayout.CENTER)

        val scrollPane = JScrollPane(table)
        scrollPane.border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor)
        layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER as Any)
        layeredPane.add(loadingPanel, JLayeredPane.PALETTE_LAYER as Any)
    }

    private fun initEvents() {

        Disposer.register(this, table)

        nav.addActionListener { reload() }

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount % 2 == 0) {
                    enterTableSelectionFolder()
                }
            }
        })

        table.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    enterTableSelectionFolder()
                }
            }
        })

        val listener = object : TransportListener, Disposable {
            override fun onTransportChanged(transport: Transport) {
                val path = transport.target.parent ?: return
                if (path.fileSystem != fileSystem) return
                if (path.name.path != workdir.name.path) return
                // 立即刷新
                reload(true)
            }

            override fun dispose() {
                transportManager.removeTransportListener(this)
            }
        }
        transportManager.addTransportListener(listener)
        Disposer.register(this, listener)

        // 变更工作目录
        if (SwingUtilities.isEventDispatchThread()) {
            changeWorkdir(homeDirectory)
        } else {
            SwingUtilities.invokeLater { changeWorkdir(homeDirectory) }
        }

    }

    private fun enterTableSelectionFolder(row: Int = table.selectedRow) {
        if (row < 0 || isLoading.get()) return
        val file = model.getFileObject(row)
        if (file.isFile) return

        // 当前工作目录
        val workdir = getWorkdir()

        // 返回上级之后，选中上级目录
        if (row == 0 && model.hasParent) {
            val workdirName = workdir.name
            nextReloadTickSelection(workdirName.baseName)
        }

        changeWorkdir(file)

    }

    private fun createRefreshButton(): JButton {
        val button = JButton(Icons.refresh)
        button.addActionListener { reload(true) }
        return button
    }

    private fun createHiddenFilesButton(): JButton {
        val button = JButton(if (useFileHiding) Icons.eyeClose else Icons.eye)
        button.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                useFileHiding = !useFileHiding
                button.icon = if (useFileHiding) Icons.eyeClose else Icons.eye
                reload(true)
            }
        })
        return button
    }

    private fun createHomeFolderButton(): JButton {
        val button = JButton(Icons.homeFolder)
        button.addActionListener { nav.changeSelectedPath(homeDirectory) }
        return button
    }

    private fun createBookmarkButton(): JButton {
        val bookmarkBtn = BookmarkButton()
        bookmarkBtn.name = "Host.${host.id}.Bookmarks"
        bookmarkBtn.addActionListener { e ->
            if (e.actionCommand.isNullOrBlank()) {
                if (bookmarkBtn.isBookmark) {
                    bookmarkBtn.deleteBookmark(workdir.absolutePathString())
                } else {
                    bookmarkBtn.addBookmark(workdir.absolutePathString())
                }
                bookmarkBtn.isBookmark = !bookmarkBtn.isBookmark
            } else {
                if (fileSystem is LocalFileSystem && SystemUtils.IS_OS_WINDOWS) {
                    val file = VFS.getManager().resolveFile("file://${e.actionCommand}")
                    if (!StringUtils.equals(file.fileSystem.rootURI, fileSystem.rootURI)) {
                        fileSystem = file.fileSystem
                    }
                    changeWorkdir(file)
                } else {
                    changeWorkdir(fileSystem.resolveFile(e.actionCommand))
                }
            }
        }

        nav.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                bookmarkBtn.isBookmark = bookmarkBtn.getBookmarks().contains(nav.getSelectedPath().absolutePathString())
            }
        })

        return bookmarkBtn
    }


    private fun createParentFolderButton(): AbstractButton {
        val button = JButton(Icons.up)
        button.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (model.rowCount < 1) return
                if (model.hasParent) enterTableSelectionFolder(0)
            }
        })

        addPropertyChangeListener("workdir") {
            button.isEnabled = model.rowCount > 0 && model.hasParent
        }

        return button
    }

    private fun nextReloadTickSelection(name: String, consumer: Consumer<Int> = Consumer { }) {
        // 创建成功之后需要修改和选中
        registerNextReloadTick {
            for (i in 0 until table.rowCount) {
                if (model.getFileObject(i).name.baseName == name) {
                    table.addRowSelectionInterval(i, i)
                    table.scrollRectToVisible(table.getCellRect(i, 0, true))
                    consumer.accept(i)
                    break
                }
            }
        }
    }

    private fun changeWorkdir(workdir: FileObject) {
        assertEventDispatchThread()
        nav.changeSelectedPath(workdir)
    }

    fun renameTo(oldPath: FileObject, newPath: FileObject) {

        // 新建文件夹
        coroutineScope.launch {

            if (requestLoading()) {
                try {
                    oldPath.moveTo(newPath)
                } catch (e: Exception) {
                    withContext(Dispatchers.Swing) {
                        OptionPane.showMessageDialog(
                            SwingUtilities.getWindowAncestor(owner),
                            ExceptionUtils.getMessage(e),
                            messageType = JOptionPane.ERROR_MESSAGE
                        )
                    }
                } finally {
                    stopLoading()
                }
            }

            // 创建成功之后需要选中
            nextReloadTickSelection(newPath.name.baseName)

            // 立即刷新
            reload()
        }
    }

    fun newFolderOrFile(name: String, isFile: Boolean) {
        coroutineScope.launch {
            if (requestLoading()) {
                try {
                    doNewFolderOrFile(getWorkdir().resolveFile(name), isFile)
                } finally {
                    stopLoading()
                }
            }

            // 创建成功之后需要修改和选中
            nextReloadTickSelection(name)

            // 立即刷新
            reload()
        }
    }


    private suspend fun doNewFolderOrFile(path: FileObject, isFile: Boolean) {

        if (path.exists()) {
            withContext(Dispatchers.Swing) {
                OptionPane.showMessageDialog(
                    owner,
                    I18n.getString("termora.transport.file-already-exists", path.name),
                    messageType = JOptionPane.ERROR_MESSAGE
                )
            }
            return
        }

        // 创建文件夹
        withContext(Dispatchers.IO) {
            runCatching { if (isFile) path.createFile() else path.createFolder() }.onFailure {
                withContext(Dispatchers.Swing) {
                    if (it is Exception) {
                        OptionPane.showMessageDialog(
                            owner,
                            ExceptionUtils.getMessage(it),
                            messageType = JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }
        }


    }


    fun requestLoading(): Boolean {
        if (isLoading.compareAndSet(false, true)) {
            if (SwingUtilities.isEventDispatchThread()) {
                loadingPanel.start()
            } else {
                SwingUtilities.invokeLater { loadingPanel.start() }
            }
            return true
        }
        return false
    }

    fun stopLoading() {
        if (isLoading.compareAndSet(true, false)) {
            if (SwingUtilities.isEventDispatchThread()) {
                loadingPanel.stop()
            } else {
                SwingUtilities.invokeLater { loadingPanel.stop() }
            }
        }
    }

    fun reload(rememberSelection: Boolean = false) {
        if (!requestLoading()) return
        if (fileSystem is MySftpFileSystem) loadingPanel.start()
        val oldWorkdir = workdir
        val path = nav.getSelectedPath()

        coroutineScope.launch {
            try {

                if (rememberSelection) {
                    withContext(Dispatchers.Swing) {
                        table.selectedRows.sortedDescending().map { model.getFileObject(it).name.baseName }
                            .forEach { nextReloadTickSelection(it) }
                    }
                }

                runCatching { model.reload(path, useFileHiding) }.onFailure {
                    if (it is Exception) {
                        withContext(Dispatchers.Swing) {
                            OptionPane.showMessageDialog(
                                owner, ExceptionUtils.getRootCauseMessage(it),
                                messageType = JOptionPane.ERROR_MESSAGE
                            )
                        }
                    }
                }.onSuccess {
                    withContext(Dispatchers.Swing) {
                        workdir = path
                        // 触发工作目录变动
                        firePropertyChange("workdir", oldWorkdir, workdir)
                    }
                }

                withContext(Dispatchers.Swing) {
                    // 触发
                    triggerNextReloadTicks()
                }

            } finally {
                stopLoading()
                if (fileSystem is MySftpFileSystem) {
                    withContext(Dispatchers.Swing) { loadingPanel.stop() }
                }
            }
        }
    }

    fun getWorkdir(): FileObject {
        return workdir
    }

    private fun registerNextReloadTick(consumer: Consumer<Unit>) {
        nextReloadTicks += Consumer<Unit> { t ->
            assertEventDispatchThread()
            consumer.accept(t)
        }
    }

    private fun triggerNextReloadTicks() {
        for (nextReloadTick in nextReloadTicks) {
            nextReloadTick.accept(Unit)
        }
        nextReloadTicks = emptyArray()
    }

    override fun dispose() {
        if (disposed.compareAndSet(false, true)) {
            val rootChildren = transportManager.getTransports(0L)
            for (child in rootChildren) {
                if (child.source.fileSystem == fileSystem ||
                    child.target.fileSystem == fileSystem
                ) {
                    child.changeStatus(TransportStatus.Failed)
                }
            }
            fileSystem.fileSystemManager.filesCache.clear(fileSystem)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        return if (dataKey == SFTPDataProviders.FileSystemViewTable) table as T else null
    }

    override fun getFileSystem(): FileSystem {
        return fileSystem
    }

    override fun setFileSystem(fileSystem: FileSystem) {
        this.fileSystem = fileSystem
    }

    private class LoadingPanel : JPanel() {
        private val busyLabel = JXBusyLabel()

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

    private class LayeredPane : JLayeredPane() {
        override fun doLayout() {
            synchronized(treeLock) {
                val w = width
                val h = height
                for (c in components) {
                    c.setBounds(0, 0, w, h)
                }
            }
        }
    }

}