package app.termora.transfer

import app.termora.*
import app.termora.transfer.InternalTransferManager.TransferMode
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.time.DateFormatUtils
import org.apache.sshd.sftp.client.fs.SftpPath
import org.slf4j.LoggerFactory
import java.awt.Component
import java.awt.Dimension
import java.awt.Window
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import javax.swing.*
import kotlin.collections.ArrayDeque
import kotlin.collections.List
import kotlin.collections.Set
import kotlin.collections.isNotEmpty
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.math.max

internal class DefaultInternalTransferManager(
    private val owner: Supplier<Window>,
    private val coroutineScope: CoroutineScope,
    private val transferManager: TransferManager,
    private val source: WorkdirProvider,
    private val target: WorkdirProvider
) : InternalTransferManager {

    companion object {
        private val log = LoggerFactory.getLogger(DefaultInternalTransferManager::class.java)
    }

    interface WorkdirProvider {
        fun getWorkdir(): Path?
    }


    private data class AskTransfer(
        val option: Int,
        val action: TransferAction,
        val applyAll: Boolean
    )

    private data class AskTransferContext(var action: TransferAction, var applyAll: Boolean)


    override fun canTransfer(paths: List<Path>): Boolean {
        val c = target.getWorkdir() ?: return false
        if (c.fileSystem.isOpen.not()) return false
        return paths.isNotEmpty()
    }

    override fun addTransfer(
        paths: List<Pair<Path, TransportTableModel.Attributes>>,
        mode: TransferMode
    ): CompletableFuture<Unit> {
        val workdir = (if (mode == TransferMode.Delete || mode == TransferMode.ChangePermission)
            source.getWorkdir() ?: target.getWorkdir() else target.getWorkdir()) ?: throw IllegalStateException()
        return addTransfer(paths, workdir, mode)
    }

    override fun addTransfer(
        paths: List<Pair<Path, TransportTableModel.Attributes>>,
        targetWorkdir: Path,
        mode: TransferMode
    ): CompletableFuture<Unit> {
        assertEventDispatchThread()

        if (paths.isEmpty()) return CompletableFuture.completedFuture(Unit)

        val future = CompletableFuture<Unit>()

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val context = AskTransferContext(TransferAction.Overwrite, false)
                for (pair in paths) {
                    if (mode == TransferMode.Transfer && context.applyAll.not()) {
                        val action = withContext(Dispatchers.Swing) {
                            getTransferAction(context, targetWorkdir.resolve(pair.first.name), pair.second)
                        }
                        if (action == null) {
                            break
                        } else if (context.applyAll) {
                            if (action == TransferAction.Skip) {
                                break
                            }
                        } else if (action == TransferAction.Skip) {
                            continue
                        }
                    }
                    val flag = doAddTransfer(targetWorkdir, pair, mode, context.action, future)
                    if (flag != FileVisitResult.CONTINUE) break
                }
                future.complete(Unit)
            } catch (e: Exception) {
                if (log.isErrorEnabled) log.error(e.message, e)
                future.completeExceptionally(e)
            }
        }
        return future
    }

    override fun addHighTransfer(source: Path, target: Path): String {
        val transfer = FileTransfer(
            parentId = StringUtils.EMPTY,
            source = source,
            target = target,
            size = Files.size(source),
            action = TransferAction.Overwrite,
            priority = Transfer.Priority.High
        )
        if (transferManager.addTransfer(transfer)) {
            return transfer.id()
        } else {
            throw IllegalStateException("Cannot add high transfer.")
        }
    }

    override fun addTransferListener(listener: TransferListener): Disposable {
        return transferManager.addTransferListener(listener)
    }

    private fun getTransferAction(
        context: AskTransferContext,
        path: Path,
        source: TransportTableModel.Attributes
    ): TransferAction? {
        if (context.applyAll) return context.action

        if (path.exists()) {
            val transfer = askTransfer(source, source)
            context.action = transfer.action
            context.applyAll = transfer.applyAll
            if (transfer.option != JOptionPane.OK_OPTION) return null
        }

        return TransferAction.Overwrite
    }


    private fun askTransfer(
        source: TransportTableModel.Attributes,
        target: TransportTableModel.Attributes
    ): AskTransfer {
        val formMargin = "7dlu"
        val layout = FormLayout(
            "left:pref, $formMargin, default:grow, 2dlu, left:pref",
            "pref, 12dlu, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, 16dlu, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
        )

        val iconSize = 36
        // @formatter:off
        val targetIcon = ScaleIcon(if(target.isDirectory) NativeIcons.folderIcon else NativeIcons.fileIcon, iconSize)
        val sourceIcon = ScaleIcon(if(source.isDirectory) NativeIcons.folderIcon else NativeIcons.fileIcon, iconSize)
        val sourceModified= DateFormatUtils.format(Date(source.lastModifiedTime), I18n.getString("termora.date-format"))
        val targetModified= DateFormatUtils.format(Date(target.lastModifiedTime), I18n.getString("termora.date-format"))
        // @formatter:on


        val actionsComBoBox = JComboBox<TransferAction>()
        actionsComBoBox.addItem(TransferAction.Overwrite)
//        actionsComBoBox.addItem(TransferAction.Append)
        actionsComBoBox.addItem(TransferAction.Skip)
        actionsComBoBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                var text = value?.toString() ?: StringUtils.EMPTY
                if (value == TransferAction.Overwrite) {
                    text = I18n.getString("termora.transport.sftp.already-exists.overwrite")
                } else if (value == TransferAction.Skip) {
                    text = I18n.getString("termora.transport.sftp.already-exists.skip")
                } else if (value == TransferAction.Append) {
                    text = I18n.getString("termora.transport.sftp.already-exists.append")
                }
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
            }
        }
        val applyAllCheckbox = JCheckBox(I18n.getString("termora.transport.sftp.already-exists.apply-all"))
        val box = Box.createHorizontalBox()
        box.add(actionsComBoBox)
        box.add(Box.createHorizontalStrut(8))
        box.add(applyAllCheckbox)
        box.add(Box.createHorizontalGlue())

        val ttBox = Box.createVerticalBox()
        ttBox.add(JLabel(I18n.getString("termora.transport.sftp.already-exists.message1")))
        ttBox.add(JLabel(I18n.getString("termora.transport.sftp.already-exists.message2")))

        val warningIcon = ScaleIcon(Icons.warningIntroduction, iconSize)

        var rows = 1
        val step = 2
        val panel = FormBuilder.create().layout(layout)
            // tip
            .add(JLabel(warningIcon)).xy(1, rows)
            .add(ttBox).xyw(3, rows, 3).apply { rows += step }
            // name
            .add(JLabel("${I18n.getString("termora.transport.sftp.already-exists.name")}:")).xy(1, rows)
            .add(source.name).xyw(3, rows, 3).apply { rows += step }
            // separator
            .addSeparator(StringUtils.EMPTY).xyw(1, rows, 5).apply { rows += step }
            // Destination
            .add("${I18n.getString("termora.transport.sftp.already-exists.destination")}:").xy(1, rows)
            .apply { rows += step }
            // Folder
            .add(JLabel(targetIcon)).xy(1, rows, "center, fill")
            .add(targetModified).xyw(3, rows, 3).apply { rows += step }
            // Source
            .add("${I18n.getString("termora.transport.sftp.already-exists.source")}:").xy(1, rows)
            .apply { rows += step }
            // Folder
            .add(JLabel(sourceIcon)).xy(1, rows, "center, fill")
            .add(sourceModified).xyw(3, rows, 3).apply { rows += step }
            // separator
            .addSeparator(StringUtils.EMPTY).xyw(1, rows, 5).apply { rows += step }
            // name
            .add(JLabel("${I18n.getString("termora.transport.sftp.already-exists.actions")}:")).xy(1, rows)
            .add(box).xyw(3, rows, 3).apply { rows += step }
            .build()
        panel.putClientProperty("SKIP_requestFocusInWindow", true)

        return AskTransfer(
            option = OptionPane.showConfirmDialog(
                owner.get(), panel,
                messageType = JOptionPane.PLAIN_MESSAGE,
                optionType = JOptionPane.OK_CANCEL_OPTION,
                title = source.name,
                initialValue = JOptionPane.OK_OPTION,
            ) {
                it.size = Dimension(max(UIManager.getInt("Dialog.width") - 220, it.width), it.height)
                it.setLocationRelativeTo(it.owner)
            },
            action = actionsComBoBox.selectedItem as TransferAction,
            applyAll = applyAllCheckbox.isSelected
        )

    }

    private fun doAddTransfer(
        workdir: Path,
        pair: Pair<Path, TransportTableModel.Attributes>,
        mode: TransferMode,
        action: TransferAction,
        future: CompletableFuture<Unit>
    ): FileVisitResult {

        val isDirectory = pair.second.isDirectory
        val path = pair.first
        if (isDirectory.not() || mode == TransferMode.Rmrf) {
            val transfer =
                createTransfer(path, workdir.resolve(path.name), isDirectory, StringUtils.EMPTY, mode, action)
            return if (transferManager.addTransfer(transfer)) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
        }

        val continued = AtomicBoolean(true)
        val queue = ArrayDeque<Transfer>()
        val isCancelled =
            { (future.isCancelled || future.isCompletedExceptionally).apply { continued.set(this.not()) } }
        val basedir = if (isDirectory) workdir.resolve(path.name) else workdir
        val visitor = object : FileVisitor<Path> {
            override fun preVisitDirectory(
                dir: Path,
                attrs: BasicFileAttributes
            ): FileVisitResult {
                val parentId = queue.lastOrNull()?.id() ?: StringUtils.EMPTY
                // @formatter:off
                val transfer = when (mode) {
                    TransferMode.Delete -> createTransfer(dir, dir, true, parentId, mode, action)
                    TransferMode.ChangePermission -> createTransfer(path, dir, true, parentId, mode, action, pair.second.permissions)
                    else -> createTransfer(dir, basedir.resolve(path.relativize(dir).pathString), true, parentId, mode, action)
                }
                // @formatter:on

                queue.addLast(transfer)

                if (transferManager.addTransfer(transfer).not()) {
                    continued.set(false)
                    return FileVisitResult.TERMINATE
                }

                return if (isCancelled.invoke()) FileVisitResult.TERMINATE else FileVisitResult.CONTINUE
            }

            override fun visitFile(
                file: Path,
                attrs: BasicFileAttributes
            ): FileVisitResult {

                val parentId = queue.last().id()
                // @formatter:off
                val transfer = when (mode) {
                    TransferMode.Delete -> createTransfer(file, file, false, parentId, mode, action)
                    TransferMode.ChangePermission -> createTransfer(file, file, false, parentId, mode, action, pair.second.permissions)
                    else -> createTransfer(file, basedir.resolve(path.relativize(file).pathString), false, parentId, mode, action)
                }

                if (transferManager.addTransfer(transfer).not()) {
                    continued.set(false)
                    return FileVisitResult.TERMINATE
                }

                return if (isCancelled.invoke()) FileVisitResult.TERMINATE else FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(
                file: Path?,
                exc: IOException
            ): FileVisitResult {
                if (log.isErrorEnabled) log.error(exc.message, exc)
                future.completeExceptionally(exc)
                return FileVisitResult.TERMINATE
            }

            override fun postVisitDirectory(
                dir: Path?,
                exc: IOException?
            ): FileVisitResult {
                val c = queue.removeLast()
                if (c is TransferScanner) c.scanned()
                return if (isCancelled.invoke()) FileVisitResult.TERMINATE else FileVisitResult.CONTINUE
            }

        }

        PathWalker.walkFileTree(path, visitor)

        // 已经添加的则继续传输
        while (queue.isNotEmpty()) {
            val c = queue.removeLast()
            if (c is TransferScanner) c.scanned()
        }

        return if (continued.get()) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
    }


    private fun createTransfer(
        source: Path,
        target: Path,
        isDirectory: Boolean,
        parentId: String,
        mode: TransferMode,
        action:TransferAction,
        permissions: Set<PosixFilePermission>? = null
    ): Transfer {
        when {
            mode == TransferMode.Delete -> {
                return DeleteTransfer(
                    parentId,
                    source,
                    isDirectory,
                    if (isDirectory) 1 else Files.size(source)
                )
            }
            mode == TransferMode.Rmrf -> {
                return CommandTransfer(
                    parentId,
                    source as SftpPath,
                    isDirectory,
                    if (isDirectory) 1 else Files.size(source),
                    "rm -rf ${source.absolutePathString()}",
                )
            }
            mode == TransferMode.ChangePermission -> {
                if (permissions == null) throw IllegalStateException()
                return ChangePermissionTransfer(
                    parentId,
                    target,
                    isDirectory = isDirectory,
                    permissions = permissions,
                    size = if (isDirectory) 1 else Files.size(target)
                )
            }
            isDirectory -> {
                return DirectoryTransfer(
                    parentId = parentId,
                    source = source,
                    target = target,
                )
            }
            else -> return FileTransfer(
                parentId = parentId,
                source = source,
                target = target,
                action = action,
                size = Files.size(source)
            )
        }
    }


}