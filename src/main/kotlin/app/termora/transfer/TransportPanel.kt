package app.termora.transfer


import app.termora.*
import app.termora.actions.DataProvider
import app.termora.database.DatabaseManager
import app.termora.plugin.ExtensionManager
import app.termora.plugin.internal.wsl.WSLHostTerminalTab
import app.termora.transfer.TransportTableModel.Attributes
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatToolBar
import com.formdev.flatlaf.icons.FlatTreeClosedIcon
import com.formdev.flatlaf.icons.FlatTreeLeafIcon
import com.formdev.flatlaf.util.SystemInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.commons.lang3.time.DateFormatUtils
import org.apache.sshd.sftp.client.fs.WithFileAttributes
import org.jdesktop.swingx.JXBusyLabel
import org.jdesktop.swingx.JXPanel
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Insets
import java.awt.Rectangle
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.*
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileOwnerAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.text.MessageFormat
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Stream
import javax.swing.*
import javax.swing.TransferHandler
import javax.swing.filechooser.FileSystemView
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableRowSorter
import javax.swing.undo.AbstractUndoableEdit
import javax.swing.undo.UndoManager
import javax.swing.undo.UndoableEdit
import kotlin.io.path.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class TransportPanel(
    private val transferManager: InternalTransferManager,
    val host: Host,
    val loader: TransportSupportLoader,
) : JPanel(BorderLayout()), DataProvider, Disposable, TransportNavigator {
    companion object {
        private val log = LoggerFactory.getLogger(TransportPanel::class.java)
        private val folderIcon = FlatTreeClosedIcon()
        private val fileIcon = FlatTreeLeafIcon()

        internal fun FileSystem.isWindowsFileSystem(): Boolean {
            return SystemInfo.isWindows && isLocallyFileSystem()
        }

        internal fun FileSystem.isLocallyFileSystem(): Boolean {
            return this == FileSystems.getDefault()
        }

    }

    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val lru = object : LinkedHashMap<String, Icon?>() {
        override fun removeEldestEntry(eldest: Map.Entry<String?, Icon?>?): Boolean {
            return size > 2048
        }
    }

    private val toolbar = FlatToolBar()
    private val homeBtn = JButton(Icons.homeFolder)
    private val prevBtn = JButton(Icons.left)
    private val nextBtn = JButton(Icons.right)
    private val eyeBtn = JButton(Icons.eye)
    private val parentBtn = JButton(Icons.up)
    private val refreshBtn = JButton(Icons.refresh)
    private val bookmarkBtn = BookmarkButton().apply { name = "Host.${host.id}.Bookmarks" }

    private val layeredPane = LayeredPane()
    private val loadingPanel = LoadingPanel()
    private val model = TransportTableModel()
    private val table = JTable(model)
    private val sorter = TableRowSorter(table.model)
    private var hasParent = false
    private val panel get() = this

    private val enableManager get() = EnableManager.getInstance()
    private val showHiddenFilesKey = "termora.transport.host.${host.id}.show-hidden-files"
    private var showHiddenFiles: Boolean
        get() = enableManager.getFlag(showHiddenFilesKey, true)
        set(value) = enableManager.setFlag(showHiddenFilesKey, value)
    private val navigator get() = this
    private val nextReloadCallbacks = mutableListOf<() -> Unit>()
    private val history = linkedSetOf<Path>()
    private val undoManager = MyUndoManager()
    private val editTransferListener = EditTransferListener()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val disposed = AtomicBoolean(false)
    private val futures = Collections.synchronizedSet(mutableSetOf<Future<*>>())


    /**
     * 工作目录
     */
    override var workdir: Path? = null
        private set

    override var loading = false
        private set(value) {
            val oldValue = field
            field = value
            if (oldValue != value) {
                firePropertyChange("loading", oldValue, value)
            }
        }

    init {
        initView()
        initEvents()
        initTableEvents()
        initTransferHandler()
    }

    private fun initView() {

        prevBtn.isEnabled = false
        nextBtn.isEnabled = false

        eyeBtn.icon = if (showHiddenFiles) Icons.eye else Icons.eyeClose

        toolbar.add(prevBtn)
        toolbar.add(homeBtn)
        toolbar.add(nextBtn)
        toolbar.add(TransportNavigationPanel(this))
        toolbar.add(bookmarkBtn)
        toolbar.add(parentBtn)
        toolbar.add(eyeBtn)
        toolbar.add(refreshBtn)

        sorter.maxSortKeys = 1
        table.setRowSorter(sorter)
        table.setAutoCreateRowSorter(false)
        table.getTableHeader().setReorderingAllowed(false)
        table.setDragEnabled(true)
        table.setDropMode(DropMode.ON_OR_INSERT_ROWS)
        table.setCellSelectionEnabled(false)
        table.setRowSelectionAllowed(true)
        table.setRowHeight(UIManager.getInt("Table.rowHeight"))
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF)
        table.setFillsViewportHeight(true)
        table.setShowGrid(true)
        table.showVerticalLines = true
        table.showHorizontalLines = true
        table.putClientProperty(
            FlatClientProperties.STYLE, mapOf(
                "cellMargins" to Insets(0, 4, 0, 4),
            )
        )
        table.columnModel.getColumn(TransportTableModel.COLUMN_NAME).preferredWidth = 220
        table.columnModel.getColumn(TransportTableModel.COLUMN_LAST_MODIFIED_TIME).preferredWidth = 120


        val sorts = mutableMapOf(
            // @formatter:off
            TransportTableModel.COLUMN_NAME to Comparator<Attributes> { o1, o2 -> NativeStringComparator.getInstance().compare(o1.name, o2.name) },
            TransportTableModel.COLUMN_FILE_SIZE to Comparator<Attributes> { o1, o2 -> o1.fileSize.compareTo(o2.fileSize) },
            TransportTableModel.COLUMN_TYPE to Comparator<Attributes> { o1, o2 -> o1.type.compareTo(o2.type) },
            TransportTableModel.COLUMN_LAST_MODIFIED_TIME to Comparator<Attributes> { o1, o2 -> o1.lastModifiedTime.compareTo(o2.lastModifiedTime) },
            TransportTableModel.COLUMN_OWNER to Comparator<Attributes> { o1, o2 -> o1.owner.compareTo(o2.owner) },
            TransportTableModel.COLUMN_ATTRS to Comparator<Attributes> { o1, o2 -> PosixFilePermissions.toString(o1.permissions).compareTo(PosixFilePermissions.toString(o2.permissions)) },
            // @formatter:on
        )

        for (i in 0 until table.columnCount) {
            sorter.setSortable(i, false)
        }

        for (e in sorts) {
            sorter.setSortable(e.key, true)
            // @formatter:off
            sorter.setComparator(e.key, Comparator<Attributes> { o1, o2 -> navigator.compare(o1, o2) ?: e.value.compare(o1, o2) })
            // @formatter:on
        }


        table.setDefaultRenderer(Any::class.java, MyDefaultTableCellRenderer())
        val scrollPane = JScrollPane(table)
        scrollPane.apply { border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor) }

        layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER as Any)
        layeredPane.add(loadingPanel, JLayeredPane.PALETTE_LAYER as Any)

        add(toolbar, BorderLayout.NORTH)
        add(layeredPane, BorderLayout.CENTER)
    }

    private fun compare(o1: Attributes, o2: Attributes): Int? {
        val sortOrder = sorter.sortKeys.first().sortOrder
        if (sortOrder == SortOrder.ASCENDING) {
            if (o1.isParent && o2.isParent) return 0
            if (o1.isParent) return -1
            if (o2.isParent) return 1
        } else {
            if (o1.isParent && o2.isParent) return 0
            if (o1.isParent) return 1
            if (o2.isParent) return -1
        }
        return null
    }

    private fun initEvents() {

        Disposer.register(this, editTransferListener)

        refreshBtn.addActionListener { reload(requestFocus = true) }

        prevBtn.addActionListener { navigator.back() }
        nextBtn.addActionListener { navigator.forward() }

        parentBtn.addActionListener(createSmartAction(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (hasParent.not()) return
                reload(newPath = model.getPath(0).absolutePathString(), requestFocus = true)
            }
        }))

        bookmarkBtn.addActionListener(createSmartAction(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val workdir = workdir ?: return
                if (e.actionCommand.isNullOrBlank()) {
                    if (bookmarkBtn.isBookmark) {
                        bookmarkBtn.deleteBookmark(workdir.pathString)
                    } else {
                        bookmarkBtn.addBookmark(workdir.pathString)
                    }
                    bookmarkBtn.isBookmark = bookmarkBtn.isBookmark.not()
                } else {
                    navigateTo(e.actionCommand)
                }
            }
        }))

        homeBtn.addActionListener(createSmartAction(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (loader.isLoaded()) {
                    val home = loader.getSyncTransportSupport().getDefaultPath().absolutePathString()
                    reload(newPath = home, requestFocus = true)
                }
            }
        }))

        eyeBtn.addActionListener(createSmartAction(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                showHiddenFiles = showHiddenFiles.not()
                eyeBtn.icon = if (showHiddenFiles) Icons.eye else Icons.eyeClose
                reload(requestFocus = true)
            }
        }))


        undoManager.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                prevBtn.isEnabled = undoManager.canUndo()
                nextBtn.isEnabled = undoManager.canRedo()
            }
        })

        // 传输完成之后刷新
        transferManager.addTransferListener(object : TransferListener {
            override fun onTransferChanged(transfer: Transfer, state: TransferTreeTableNode.State) {
                if (state != TransferTreeTableNode.State.Done && state != TransferTreeTableNode.State.Failed) return
                val target = transfer.target()
                if (loader.isLoaded()) {
                    if (target.fileSystem != loader.getSyncTransportSupport().getFileSystem()) return
                }
                if (target.pathString == workdir?.pathString || target.parent.pathString == workdir?.pathString) {
                    reload(requestFocus = false)
                }
            }
        }).let { Disposer.register(this, it) }

        // High 专门用于编辑目的，下载完成之后立即去编辑
        transferManager.addTransferListener(editTransferListener).let { Disposer.register(this, it) }

        // parent button
        addPropertyChangeListener("loading") { evt ->
            if (evt.newValue == false) {
                parentBtn.isEnabled = hasParent
            }
        }

        // loading ui
        addPropertyChangeListener("loading", object : PropertyChangeListener {
            private var job: Job? = null
            override fun propertyChange(evt: PropertyChangeEvent) {
                job?.cancel()
                job = null
                val loading = evt.newValue == true
                if (loading) {
                    job = coroutineScope.launch(Dispatchers.Unconfined) {
                        delay(150.milliseconds)
                        withContext(Dispatchers.Swing) {
                            loadingPanel.busyLabel.isBusy = true
                            loadingPanel.isVisible = true
                        }
                    }
                } else {
                    loadingPanel.busyLabel.isBusy = false
                    loadingPanel.isVisible = false
                }
            }
        })

        // history
        addPropertyChangeListener("workdir") { evt ->
            val newValue = evt.newValue
            if (newValue is Path) {
                if ((newValue.fileSystem.isWindowsFileSystem() && newValue.fileSystem.separator == newValue.pathString).not()) {
                    history.add(newValue)
                }
            }
        }

        // bookmark
        addPropertyChangeListener("workdir") { evt ->
            val newValue = evt.newValue
            if (newValue is Path) {
                bookmarkBtn.isBookmark = bookmarkBtn.getBookmarks().contains(newValue.pathString)
            }
        }

        // undo or redo
        addPropertyChangeListener("workdir", object : PropertyChangeListener {
            private var undoOrRedo = false
            private var undoOrRedoPath: Path? = null

            override fun propertyChange(evt: PropertyChangeEvent) {
                val newValue = evt.newValue
                val oldValue = evt.oldValue
                if (newValue is Path && oldValue is Path) {
                    if (undoOrRedo && (undoOrRedoPath == newValue || undoOrRedoPath == oldValue)) {
                        undoOrRedo = false
                        return
                    }
                    undoManager.addEdit(object : AbstractUndoableEdit() {
                        override fun undo() {
                            super.undo()
                            if (navigator.reload(newPath = oldValue.absolutePathString(), requestFocus = true)) {
                                undoOrRedo = true
                                undoOrRedoPath = oldValue
                            }
                        }

                        override fun redo() {
                            super.redo()
                            if (navigator.reload(newPath = newValue.absolutePathString(), requestFocus = true)) {
                                undoOrRedo = true
                                undoOrRedoPath = newValue
                            }
                        }

                        override fun getPresentationName(): String {
                            return "Path"
                        }
                    })

                }
            }
        })

        addPropertyChangeListener("workdir") { evt -> reload() }

        reload()
    }

    private fun initTableEvents() {

        table.tableHeader.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val column = table.tableHeader.columnAtPoint(e.point)
                    if (column < 0) return
                    sorter.sortKeys = null
                }
            }
        })

        // https://github.com/TermoraDev/termora/issues/401
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == 4) {
                    back()
                } else if (e.button == 5) {
                    forward()
                }
            }
        })

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount % 2 == 0) {
                    var row = table.selectedRow
                    if (row < 0) return
                    row = sorter.convertRowIndexToModel(table.selectedRow)
                    val attributes = model.getAttributes(row)
                    if (attributes.isDirectory) {
                        enterSelectionFolder()
                    } else {
                        val paths = listOf(model.getPath(row) to attributes)
                        if (loader.isOpened() && transferManager.canTransfer(paths.map { it.first })) {
                            transferManager.addTransfer(paths, InternalTransferManager.TransferMode.Transfer)
                        }
                    }
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    val r = table.rowAtPoint(e.point)
                    if (r >= 0 && r < table.rowCount) {
                        if (!table.isRowSelected(r)) {
                            table.setRowSelectionInterval(r, r)
                        }
                    } else {
                        table.clearSelection()
                    }

                    if (table.hasFocus().not()) {
                        table.requestFocusInWindow()
                    }

                    val rows = table.selectedRows.map { sorter.convertRowIndexToModel(it) }.toTypedArray()
                    showContextmenu(rows, e)
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    val row = table.rowAtPoint(e.point)
                    if (row < 0) table.clearSelection()
                }
            }
        })

        table.actionMap.put("Reload", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                reload()
            }
        })

        table.actionMap.put("EnterSelectionFolder", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                enterSelectionFolder()
            }
        })

        // 快速导航
        table.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.modifiersEx > 0) return
                val c = e.keyChar
                val count = model.rowCount
                val row = table.selectedRow + 1
                for (i in row until count) if (navigate(i, c)) return
                for (i in 0 until count) if (navigate(i, c)) return
            }

            private fun navigate(row: Int, c: Char): Boolean {
                val row = sorter.convertRowIndexToModel(row)
                val name = model.getAttributes(row).name
                if (name.startsWith(c, true)) {
                    table.setRowSelectionInterval(row, row)
                    table.scrollRectToVisible(table.getCellRect(row, 0, true))
                    return true
                }
                return false
            }
        })

        val inputMap = table.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        if (SystemInfo.isMacOS.not()) {
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "Reload")
        }
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "EnterSelectionFolder")
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, toolkit.menuShortcutKeyMaskEx), "Reload")
    }

    private fun initTransferHandler() {
        data class TransferData(
            // true 就是本地拖拽上传
            val locally: Boolean,
            val row: Int,
            val insertRow: Boolean,
            val workdir: Path,
            val files: List<Pair<Path, Attributes>>
        )



        table.transferHandler = object : TransferHandler() {
            override fun canImport(support: TransferSupport): Boolean {
                return getTransferData(support, false) != null
            }

            override fun importData(support: TransferSupport): Boolean {
                val data = getTransferData(support, true) ?: return false

                val future = transferManager
                    .addTransfer(data.files, data.workdir, InternalTransferManager.TransferMode.Transfer)

                mountFuture(future)

                return true
            }

            private fun getTransferData(support: TransferSupport, load: Boolean): TransferData? {
                val workdir = workdir ?: return null
                val dropLocation = support.dropLocation as? JTable.DropLocation ?: return null
                val row = if (dropLocation.isInsertRow) 0 else sorter.convertRowIndexToModel(dropLocation.row)
                if (dropLocation.isInsertRow.not() && dropLocation.column != TransportTableModel.COLUMN_NAME) return null
                if (dropLocation.isInsertRow.not() && model.getAttributes(row).isDirectory.not()) return null
                if (hasParent && dropLocation.row == 0) return null
                val paths = mutableListOf<Pair<Path, Attributes>>()
                var locally = false

                if (support.isDataFlavorSupported(TransferTransferable.FLAVOR)) {
                    val transferTransferable = support.transferable.getTransferData(TransferTransferable.FLAVOR)
                            as? TransferTransferable ?: return null
                    if (transferTransferable.component == panel) return null
                    paths.addAll(transferTransferable.files)
                } else if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    if (loader.isLoaded() && loader.getSyncTransportSupport().getFileSystem().isLocallyFileSystem())
                        return null
                    if (load) {
                        val files = support.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
                        if (files.isEmpty()) return null
                        for (file in files.filterIsInstance<File>()) {
                            val path = file.toPath()
                            paths.add(path to getAttributes(path))
                        }
                    }
                    locally = true
                } else {
                    return null
                }

                return TransferData(
                    locally = locally,
                    row = row,
                    insertRow = dropLocation.isInsertRow,
                    workdir = if (dropLocation.isInsertRow) workdir else model.getPath(row),
                    files = paths
                )
            }

            override fun getSourceActions(c: JComponent?): Int {
                return COPY
            }

            override fun createTransferable(c: JComponent?): Transferable? {
                val rows = table.selectedRows.map { sorter.convertRowIndexToModel(it) }.toTypedArray()
                if (rows.isEmpty()) return null
                return TransferTransferable(panel, rows.map { model.getPath(it) to model.getAttributes(it) })
            }

        }

    }

    private suspend fun getFileSystem(): FileSystem {
        return loader.getTransportSupport().getFileSystem()
    }

    private suspend fun getTransportSupport(): TransportSupport {
        return loader.getTransportSupport()
    }

    private fun enterSelectionFolder() {
        var row = table.selectedRow
        if (row < 0) return
        row = sorter.convertRowIndexToModel(table.selectedRow)
        val attributes = model.getAttributes(row)
        if (attributes.isDirectory.not()) return

        // 记住当前目录名称
        val path = model.getPath(row)
        if (attributes.isParent) {
            val workdir = workdir
            if (workdir != null) registerSelectRow(workdir.name)
        }

        // Windows 比较特殊，显示盘符页
        if (path.fileSystem.isWindowsFileSystem() && path.pathString == path.fileSystem.separator) {
            navigator.navigateTo(path.pathString)
        } else {
            navigator.navigateTo(path.absolutePathString())
        }
    }

    private fun registerSelectRow(name: String) {
        nextReloadCallbacks.add {
            for (i in 0 until model.rowCount) {
                if (model.getAttributes(i).name == name) {
                    val c = sorter.convertRowIndexToView(i)
                    table.clearSelection()
                    table.setRowSelectionInterval(c, c)
                    table.scrollRectToVisible(table.getCellRect(c, TransportTableModel.COLUMN_NAME, true))
                    break
                }
            }
        }
    }

    fun reload(
        oldPath: String? = workdir?.absolutePathString(),
        newPath: String? = workdir?.absolutePathString(),
        requestFocus: Boolean = false
    ): Boolean {
        assertEventDispatchThread()

        if (loading) return false
        loading = true

        coroutineScope.launch {
            try {

                val workdir = doReload(oldPath, newPath, requestFocus)

                withContext(Dispatchers.Swing) {
                    setNewWorkdir(workdir)
                    nextReloadCallbacks.forEach { runCatching { it.invoke() } }
                }

            } catch (e: Exception) {
                if (log.isErrorEnabled) log.error(e.message, e)
                coroutineScope.launch(Dispatchers.Swing) {
                    OptionPane.showMessageDialog(
                        owner,
                        ExceptionUtils.getRootCauseMessage(e),
                        messageType = JOptionPane.ERROR_MESSAGE
                    )
                }
            } finally {
                withContext(Dispatchers.Swing) {
                    loading = false
                    nextReloadCallbacks.clear()
                }
            }
        }

        return true
    }

    private suspend fun doReload(
        oldPath: String? = null,
        newPath: String? = null,
        requestFocus: Boolean = false
    ): Path {

        val support = getTransportSupport()
        val fileSystem = support.getFileSystem()
        val workdir = newPath ?: oldPath

        if (workdir == null) {
            val path = support.getDefaultPath()
            return doReload(null, path.absolutePathString())
        }

        val path = fileSystem.getPath(workdir)
        val first = AtomicBoolean(false)
        var parent = path.parent
        if (parent == null && fileSystem.isWindowsFileSystem() && path.pathString != fileSystem.separator) {
            parent = fileSystem.getPath(fileSystem.separator)
        }
        val files = mutableListOf<Pair<Path, Attributes>>()
        if ((parent != null).also { hasParent = it }) {
            val attributes = getAttributes(parent)
            files.add(parent to attributes.copy(name = "..", lastModifiedTime = Long.MIN_VALUE))
        }

        val consume = suspend {
            withContext(Dispatchers.Swing) {
                if (first.compareAndSet(false, true)) {
                    model.clear()
                    table.scrollRectToVisible(Rectangle())
                }
                for (pair in files) {
                    model.addRow(arrayOf(pair.first, pair.second))
                }
            }
            files.clear()
        }

        if (fileSystem.isWindowsFileSystem() && path.pathString == fileSystem.separator) {
            for (path in fileSystem.rootDirectories) {
                val attributes = getAttributes(path)
                files.add(path to attributes)
            }
        } else {
            listFiles(path).use { paths ->
                for (item in paths) {
                    files.add(item)
                    if (files.size > 50) consume.invoke()
                }
            }
        }

        if (files.isNotEmpty())
            consume.invoke()

        if (requestFocus)
            coroutineScope.launch(Dispatchers.Swing) { table.requestFocusInWindow() }

        return path
    }

    private fun listFiles(path: Path): Stream<Pair<Path, Attributes>> {
        val stream = Files.list(path)
            .map { it to getAttributes(it) }
            // @formatter:off
            .sorted(compareBy<Pair<Path, Attributes>> { it.second.isDirectory.not() }.thenComparing { a, b -> NativeStringComparator.getInstance().compare(a.second.name, b.second.name) })
            // @formatter:on

        if (showHiddenFiles.not()) {
            return stream.filter { it.second.name.startsWith(".").not() }
        }

        return stream
    }

    private fun getAttributes(path: Path): Attributes {
        if (path is WithFileAttributes) {
            val attributes = path.attributes
            if (attributes != null) {
                return Attributes(
                    name = path.name,
                    type = Attributes.computeType(attributes.isSymbolicLink, attributes.isDirectory, path.name),
                    isDirectory = attributes.isDirectory,
                    isFile = attributes.isRegularFile,
                    isSymbolicLink = attributes.isSymbolicLink,
                    fileSize = attributes.size,
                    permissions = fromSftpPermissions(attributes.permissions),
                    owner = attributes.owner ?: StringUtils.EMPTY,
                    lastModifiedTime = attributes.modifyTime.toMillis()
                )
            }
        }

        val basicAttributes = runCatching { path.fileAttributesView<BasicFileAttributeView>().readAttributes() }
            .getOrNull()
        val fileOwnerAttribute = runCatching { path.fileAttributesView<FileOwnerAttributeView>().owner }
            .getOrNull()
        val posixFileAttribute = runCatching { path.fileAttributesView<PosixFileAttributeView>().readAttributes() }
            .getOrNull()

        val fileSize = basicAttributes?.size() ?: 0
        val permissions = posixFileAttribute?.permissions() ?: emptySet()
        val owner = fileOwnerAttribute?.name ?: StringUtils.EMPTY
        val lastModifiedTime = basicAttributes?.lastModifiedTime()?.toMillis() ?: 0
        val isDirectory = basicAttributes?.isDirectory ?: false
        val isSymbolicLink = basicAttributes?.isSymbolicLink ?: false

        return Attributes(
            name = StringUtils.defaultIfBlank(path.name, path.pathString),
            type = Attributes.computeType(isSymbolicLink, isDirectory, path.name),
            isDirectory = isDirectory,
            isFile = basicAttributes?.isRegularFile ?: false,
            isSymbolicLink = isSymbolicLink,
            fileSize = fileSize,
            permissions = permissions,
            owner = owner,
            lastModifiedTime = lastModifiedTime
        )
    }

    private fun createSmartAction(action: Action): Action {
        return object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (loading) return
                action.actionPerformed(e)
            }
        }
    }

    private fun showContextmenu(rows: Array<Int>, e: MouseEvent) {
        val files = rows.map { model.getPath(it) to model.getAttributes(it) }
        val popupMenu = TransportPopupMenu(owner, model, transferManager, loader, files)
        popupMenu.addActionListener(PopupMenuActionListener(files))
        popupMenu.show(table, e.x, e.y)
    }


    override fun navigateTo(destination: String): Boolean {
        assertEventDispatchThread()

        if (loading) return false

        if (loader.isOpened()) {
            if (workdir?.pathString == destination) return false
        }

        return reload(newPath = destination)
    }

    override fun getHistory(): List<Path> {
        return history.toList()
    }

    override fun canRedo(): Boolean {
        return undoManager.canRedo() && loading.not()
    }

    override fun canUndo(): Boolean {
        return undoManager.canUndo() && loading.not()
    }

    override fun back() {
        if (loading.not() && undoManager.canUndo()) undoManager.undo()
    }

    override fun forward() {
        if (loading.not() && undoManager.canRedo()) undoManager.redo()
    }

    private fun setNewWorkdir(destination: Path) {
        val oldValue = if (destination.fileSystem == workdir?.fileSystem) workdir else null
        workdir = destination
        firePropertyChange("workdir", oldValue, destination)
    }


    private fun mountFuture(future: CompletableFuture<*>) {
        if (disposed.get()) return
        futures.add(future)
        future.whenComplete { _, e ->
            if (disposed.get().not() && e is Exception) {
                SwingUtilities.invokeLater {
                    OptionPane.showMessageDialog(
                        owner,
                        ExceptionUtils.getRootCauseMessage(e),
                        messageType = JOptionPane.ERROR_MESSAGE
                    )
                }
            }
            futures.remove(future)
        }
    }

    override fun dispose() {
        if (disposed.compareAndSet(false, true)) {
            futures.forEach { it.cancel(true) }
            futures.clear()
            coroutineScope.cancel()
            loadingPanel.busyLabel.isBusy = false
        }
    }

    private class TransferTransferable(val component: TransportPanel, val files: List<Pair<Path, Attributes>>) :
        Transferable {
        companion object {
            val FLAVOR = DataFlavor("termora/transfers", "Termora transfers")
        }

        override fun getTransferDataFlavors(): Array<out DataFlavor> {
            return arrayOf(FLAVOR)
        }

        override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean {
            return flavor == FLAVOR
        }

        override fun getTransferData(flavor: DataFlavor?): Any {
            return if (flavor == FLAVOR) this else throw UnsupportedFlavorException(flavor)
        }

    }

    private inner class EditTransferListener : TransferListener, Disposable {
        private val transferIds = mutableSetOf<String>()
        private val sftp get() = DatabaseManager.getInstance().sftp
        private val parentDisposable = Disposer.newDisposable()

        init {
            Disposer.register(this, parentDisposable)
        }

        override fun onTransferChanged(
            transfer: Transfer,
            state: TransferTreeTableNode.State
        ) {
            // 只处理最终状态
            if (state != TransferTreeTableNode.State.Done && state != TransferTreeTableNode.State.Failed) return
            // 不存在的任务则不需要监听
            if (transferIds.contains(transfer.id()).not()) return
            if (state == TransferTreeTableNode.State.Done) {
                listenFileChanged(transfer.target(), transfer.source())
            }
        }

        private fun listenFileChanged(localPath: Path, target: Path) {

            val disposable = startEditor(localPath)

            val job = coroutineScope.launch {
                var oldMillis = Files.getLastModifiedTime(localPath).toMillis()
                while (coroutineScope.isActive) {
                    delay(1.seconds)

                    if (Files.exists(localPath).not()) break
                    val millis = Files.getLastModifiedTime(localPath).toMillis()
                    if (oldMillis == millis) continue

                    // 正在编辑时可能会出现断线的情况 ，安全获取
                    val fs = getFileSystem()
                    if (fs.isOpen.not()) continue

                    // 发送到服务器
                    transferManager.addHighTransfer(localPath, fs.getPath(target.absolutePathString()))
                    oldMillis = millis
                }
            }

            val disposed = AtomicBoolean(false)
            Disposer.register(disposable, object : Disposable {
                override fun dispose() {
                    disposed.compareAndSet(false, true)
                }
            })

            Disposer.register(parentDisposable, object : Disposable {
                override fun dispose() {
                    job.cancel()
                    if (disposed.compareAndSet(false, true)) {
                        Disposer.dispose(disposable)
                    }
                }
            })

        }

        private fun startEditor(localPath: Path): Disposable {
            val editCommand = sftp.editCommand
            val extension = ExtensionManager.getInstance()
                .getExtensions(TransportEditFileExtension::class.java).firstOrNull()

            if (editCommand.isBlank() && extension != null) {
                try {
                    return extension.edit(owner, localPath)
                } catch (e: Exception) {
                    OptionPane.showMessageDialog(
                        owner,
                        ExceptionUtils.getRootCauseMessage(e),
                        messageType = JOptionPane.ERROR_MESSAGE
                    )
                }
            }

            val disposed = AtomicBoolean(false)
            val disposable = object : Disposable {
                override fun dispose() {
                    disposed.compareAndSet(false, true)
                }
            }

            try {

                val p = localPath.absolutePathString()
                if (editCommand.isNotBlank()) {
                    ProcessBuilder(WSLHostTerminalTab.parseCommand(MessageFormat.format(editCommand, p))).start()
                } else if (SystemInfo.isMacOS) {
                    ProcessBuilder("open", "-a", "TextEdit", "-W", p).start().onExit()
                        .whenComplete { _, _ -> if (disposed.get().not()) Disposer.dispose(disposable) }
                } else if (SystemInfo.isWindows) {
                    ProcessBuilder("notepad", p).start().onExit()
                        .whenComplete { _, _ -> if (disposed.get().not()) Disposer.dispose(disposable) }
                }

            } catch (e: Exception) {
                if (log.isErrorEnabled) log.error(e.message, e)
                OptionPane.showMessageDialog(
                    owner,
                    ExceptionUtils.getRootCauseMessage(e),
                    messageType = JOptionPane.ERROR_MESSAGE
                )
            }

            return disposable
        }

        override fun dispose() {
            transferIds.clear()
        }

        fun addListenTransfer(id: String) {
            transferIds.add(id)
        }
    }

    private inner class PopupMenuActionListener(private val files: List<Pair<Path, Attributes>>) : ActionListener {
        @Suppress("CascadeIf")
        override fun actionPerformed(e: ActionEvent) {
            val actionCommand = TransportPopupMenu.ActionCommand.valueOf(e.actionCommand)
            if (actionCommand == TransportPopupMenu.ActionCommand.Transfer) {
                transfer(InternalTransferManager.TransferMode.Transfer)
            } else if (actionCommand == TransportPopupMenu.ActionCommand.Delete) {
                transfer(InternalTransferManager.TransferMode.Delete)
            } else if (actionCommand == TransportPopupMenu.ActionCommand.Refresh) {
                reload(requestFocus = true)
            } else if (actionCommand == TransportPopupMenu.ActionCommand.Edit) {
                edit()
            } else if (actionCommand == TransportPopupMenu.ActionCommand.NewFolder || actionCommand == TransportPopupMenu.ActionCommand.NewFile) {
                val name = e.source.toString()
                val workdir = workdir ?: return
                processPath(e.source.toString()) {
                    // 因为此时可能已经断线，任何 Path 都不可完全相信
                    val path = getFileSystem().getPath(workdir.resolve(name).absolutePathString())
                    if (actionCommand == TransportPopupMenu.ActionCommand.NewFolder)
                        path.createDirectories()
                    else
                        path.createFile()
                }
            } else if (actionCommand == TransportPopupMenu.ActionCommand.Rename) {
                val source = files.first().first
                val target = source.parent.resolve(e.source.toString())
                processPath(e.source.toString()) { source.moveTo(target) }
            } else if (actionCommand == TransportPopupMenu.ActionCommand.Rmrf) {
                transferManager.addTransfer(files, InternalTransferManager.TransferMode.Rmrf)
            } else if (actionCommand == TransportPopupMenu.ActionCommand.Reconnect) {
                // reload now
                reload()
            } else if (actionCommand == TransportPopupMenu.ActionCommand.ChangePermissions) {
                val c = e.source as TransportPopupMenu.ChangePermission
                val path = files.first().first
                processPath(path.name) {
                    if (c.includeSubFolder) {
                        val future = withContext(Dispatchers.Swing) {
                            transferManager.addTransfer(
                                listOf(path to files.first().second.copy(permissions = c.permissions)),
                                InternalTransferManager.TransferMode.ChangePermission
                            )
                        }
                        mountFuture(future)
                        future.get()
                    } else {
                        Files.setPosixFilePermissions(path, c.permissions)
                    }
                }
            }
        }

        private fun transfer(mode: InternalTransferManager.TransferMode) {
            val future = transferManager.addTransfer(files, mode)
            mountFuture(future)
        }

        private fun edit() {
            for (path in files.map { it.first }) {
                val target = Application.createSubTemporaryDir().resolve(path.name)
                val transferId = transferManager.addHighTransfer(path, target)
                editTransferListener.addListenTransfer(transferId)
            }
        }

        private fun processPath(name: String, action: suspend () -> Unit) {
            coroutineScope.launch {
                try {
                    action.invoke()
                    withContext(Dispatchers.Swing) {
                        if (name.isNotBlank()) registerSelectRow(name)
                        reload()
                    }
                } catch (_: CancellationException) {
                } catch (e: Exception) {
                    if (log.isErrorEnabled) log.error(e.message, e)
                    withContext(Dispatchers.Swing) {
                        OptionPane.showMessageDialog(
                            owner,
                            ExceptionUtils.getRootCauseMessage(e),
                            messageType = JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }
        }

    }

    private class LayeredPane : JLayeredPane() {
        override fun doLayout() {
            synchronized(treeLock) {
                for (c in components) {
                    c.setBounds(0, 0, width, height)
                }
            }
        }
    }

    private inner class LoadingPanel : JXPanel() {
        val busyLabel = JXBusyLabel()

        init {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(50, 0, 0, 0)
            add(busyLabel, BorderLayout.CENTER)
            addMouseListener(object : MouseAdapter() {})
            isVisible = false
        }

    }

    private inner class MyDefaultTableCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component? {
            val attributes = value as? Attributes
            if (attributes == null) {
                return super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column)
            }

            var text = when (column) {
                TransportTableModel.COLUMN_NAME -> attributes.name
                TransportTableModel.COLUMN_TYPE -> getType(row, attributes)
                TransportTableModel.COLUMN_FILE_SIZE -> formatBytes(attributes.fileSize)
                // @formatter:off
                TransportTableModel.COLUMN_LAST_MODIFIED_TIME -> DateFormatUtils.format(Date(attributes.lastModifiedTime), I18n.getString("termora.date-format"))
                // @formatter:on
                TransportTableModel.COLUMN_ATTRS -> PosixFilePermissions.toString(attributes.permissions)
                TransportTableModel.COLUMN_OWNER -> attributes.owner
                else -> StringUtils.EMPTY
            }

            // 父行只显示名称
            if (attributes.isParent && column != TransportTableModel.COLUMN_NAME) {
                text = StringUtils.EMPTY
            }

            foreground = null
            val c = super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column)
            icon = null

            if (column == TransportTableModel.COLUMN_NAME) {
                val path = model.getPath(sorter.convertRowIndexToModel(row))
                if (path.fileSystem.isWindowsFileSystem()) {
                    icon = if (attributes.isParent) {
                        NativeIcons.folderIcon
                    } else {
                        lru.computeIfAbsent(path.absolutePathString()) {
                            FileSystemView.getFileSystemView().getSystemIcon(File(it))
                        }
                    }
                }

                if (SystemInfo.isLinux) {
                    icon = if (attributes.isDirectory) {
                        folderIcon
                    } else {
                        fileIcon
                    }
                }

                if (icon == null) {
                    icon = if (attributes.isDirectory) {
                        NativeIcons.folderIcon
                    } else {
                        NativeIcons.fileIcon
                    }
                }
            }

            if (loader.isOpened().not()) {
                if (isSelected.not()) {
                    foreground = UIManager.getColor("textInactiveText")
                }
            }

            return c
        }

        private fun getType(row: Int, attributes: Attributes): String {
            val path = model.getPath(sorter.convertRowIndexToModel(row))
            if (path is WithPathAttributes) {
                val type = path.getCustomType()
                if (type != null) return type
            }
            return attributes.type
        }
    }

    private inner class MyUndoManager : UndoManager() {
        init {
            limit = 128
        }

        private var listeners = emptyArray<ActionListener>()

        override fun undo() {
            super.undo()
            fireActionPerformed("undo")
        }

        override fun redo() {
            super.redo()
            fireActionPerformed("redo")
        }

        override fun undoOrRedo() {
            super.undoOrRedo()
            fireActionPerformed("undoOrRedo")
        }

        override fun undoTo(edit: UndoableEdit?) {
            super.undoTo(edit)
            fireActionPerformed("undoTo")
        }

        override fun redoTo(edit: UndoableEdit?) {
            super.redoTo(edit)
            fireActionPerformed("redoTo")
        }

        override fun addEdit(anEdit: UndoableEdit?): Boolean {
            val c = super.addEdit(anEdit)
            fireActionPerformed("addEdit")
            return c
        }

        fun addActionListener(listener: ActionListener) {
            listeners += listener
        }

        fun removeActionListener(listener: ActionListener) {
            listeners = ArrayUtils.removeElement(listeners, listener)
        }

        private fun fireActionPerformed(command: String) {
            listeners.forEach { it.actionPerformed(ActionEvent(this, ActionEvent.ACTION_PERFORMED, command)) }
        }
    }
}