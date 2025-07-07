package app.termora.transfer

import app.termora.Application
import app.termora.ApplicationScope
import app.termora.I18n
import app.termora.OptionPane
import app.termora.plugin.ExtensionManager
import app.termora.transfer.TransportPanel.Companion.isLocallyFileSystem
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.sshd.sftp.client.fs.SftpFileSystem
import java.awt.Window
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.event.EventListenerList
import kotlin.io.path.absolutePathString
import kotlin.io.path.name


internal class TransportPopupMenu(
    private val owner: Window,
    private val model: TransportTableModel,
    private val transferManager: InternalTransferManager,
    private val loader: TransportSupportLoader,
    private val files: List<Pair<Path, TransportTableModel.Attributes>>
) : FlatPopupMenu() {
    private val paths = files.map { it.first }
    private val hasParent = files.any { it.second.isParent }

    private val transferMenu = JMenuItem(I18n.getString("termora.transport.table.contextmenu.transfer"))
    private val editMenu = JMenuItem(I18n.getString("termora.transport.table.contextmenu.edit"))
    private val copyPathMenu = JMenuItem(I18n.getString("termora.transport.table.contextmenu.copy-path"))
    private val openInFinderMenu = JMenuItem(I18n.getString("termora.transport.table.contextmenu.open-in-folder"))
    private val renameMenu = JMenuItem(I18n.getString("termora.transport.table.contextmenu.rename"))
    private val deleteMenu = JMenuItem(I18n.getString("termora.transport.table.contextmenu.delete"))

    // @formatter:off
    private val changePermissionsMenu = JMenuItem(I18n.getString("termora.transport.table.contextmenu.change-permissions"))
    // @formatter:on
    private val refreshMenu = JMenuItem(I18n.getString("termora.transport.table.contextmenu.refresh"))
    private val newMenu = JMenu(I18n.getString("termora.transport.table.contextmenu.new"))
    private val newFolderMenu = newMenu.add(I18n.getString("termora.transport.table.contextmenu.new.folder"))
    private val newFileMenu = newMenu.add(I18n.getString("termora.transport.table.contextmenu.new.file"))

    private val extensionManager get() = ExtensionManager.getInstance()
    private val eventListeners = EventListenerList()
    private val mnemonics = mapOf(
        refreshMenu to KeyEvent.VK_R,
        newMenu to KeyEvent.VK_W,
        newFolderMenu to KeyEvent.VK_F,
        renameMenu to KeyEvent.VK_M,
        deleteMenu to KeyEvent.VK_D,
        editMenu to KeyEvent.VK_E,
        transferMenu to KeyEvent.VK_T,
    )

    init {
        initView()
        initEvents()
    }

    private fun initView() {
        inheritsPopupMenu = false

        if (loader.isOpened().not()) {
            val reconnect = add(I18n.getString("termora.tabbed.contextmenu.reconnect"))
            reconnect.addActionListener { e -> fireActionPerformed(e, ActionCommand.Reconnect) }
            return
        }

        val fileSystem = if (loader.isLoaded()) loader.getSyncTransportSupport().getFileSystem() else null

        add(transferMenu)
        add(editMenu)
        addSeparator()
        add(copyPathMenu)
        if (fileSystem?.isLocallyFileSystem() == true) {
            add(openInFinderMenu)
        }
        addSeparator()
        add(renameMenu)
        add(deleteMenu)
        add(changePermissionsMenu)

        val menus = mutableListOf<JMenuItem>()
        for (extension in extensionManager.getExtensions(TransportContextMenuExtension::class.java)) {
            try {
                val menu = extension.createJMenuItem(
                    ApplicationScope.forWindowScope(owner),
                    fileSystem,
                    this,
                    files
                )
                menus.add(menu)
            } catch (_: UnsupportedOperationException) {
                continue
            }
        }

        if (menus.isNotEmpty()) {
            addSeparator()
            menus.forEach { add(it) }
        }

        addSeparator()
        add(refreshMenu)
        addSeparator()

        add(newMenu)

        // 开发环境提供断线
        if (Application.getAppPath().isBlank() && loader.isOpened()) {
            addSeparator()
            add("Disconnect").addActionListener {
                IOUtils.closeQuietly(loader.getSyncTransportSupport().getFileSystem())
            }
        }

        transferMenu.isEnabled = hasParent.not() && files.isNotEmpty() && transferManager.canTransfer(paths)
        copyPathMenu.isEnabled = files.isNotEmpty()
        openInFinderMenu.isEnabled = files.isNotEmpty() && fileSystem?.isLocallyFileSystem() == true
        editMenu.isEnabled = files.isNotEmpty() && fileSystem?.isLocallyFileSystem() != true
                && files.all { it.second.isFile && it.second.isSymbolicLink.not() }
        renameMenu.isEnabled = hasParent.not() && files.size == 1
        deleteMenu.isEnabled = hasParent.not() && files.isNotEmpty()
        changePermissionsMenu.isVisible = hasParent.not() && fileSystem is SftpFileSystem && files.size == 1

        for ((item, mnemonic) in mnemonics) {
            item.text = "${item.text}(${KeyEvent.getKeyText(mnemonic)})"
            item.setMnemonic(mnemonic)
        }
    }

    private fun initEvents() {
        transferMenu.addActionListener { fireActionPerformed(it, ActionCommand.Transfer) }
        deleteMenu.addActionListener {
            if (OptionPane.showConfirmDialog(
                    owner,
                    I18n.getString("termora.keymgr.delete-warning"),
                    messageType = JOptionPane.WARNING_MESSAGE
                ) == JOptionPane.YES_OPTION
            ) {
                fireActionPerformed(it, ActionCommand.Delete)
            }
        }

        renameMenu.addActionListener { newFolderOrNewFile(it, ActionCommand.Rename) }
        editMenu.addActionListener { fireActionPerformed(it, ActionCommand.Edit) }
        newFolderMenu.addActionListener { newFolderOrNewFile(it, ActionCommand.NewFolder) }
        newFileMenu.addActionListener { newFolderOrNewFile(it, ActionCommand.NewFile) }
        refreshMenu.addActionListener { fireActionPerformed(it, ActionCommand.Refresh) }
        openInFinderMenu.addActionListener { for (path in paths) Application.browseInFolder(path.toFile()) }
        changePermissionsMenu.addActionListener { changePosixFilePermission(it) }
        copyPathMenu.addActionListener {
            val sb = StringBuilder()
            paths.forEach { sb.append(it.absolutePathString()).appendLine() }
            sb.deleteCharAt(sb.length - 1)
            toolkit.systemClipboard.setContents(StringSelection(sb.toString()), null)
        }
    }

    fun fireActionPerformed(evt: ActionEvent, command: ActionCommand) {
        for (listener in eventListeners.getListeners(ActionListener::class.java)) {
            listener.actionPerformed(ActionEvent(evt.source, evt.id, command.name))
        }
    }

    private fun changePosixFilePermission(evt: ActionEvent) {
        val panel = PosixFilePermissionPanel(files.first().second.permissions)
        if (OptionPane.showConfirmDialog(
                owner, panel,
                messageType = JOptionPane.PLAIN_MESSAGE,
                optionType = JOptionPane.OK_CANCEL_OPTION
            ) != JOptionPane.OK_OPTION
        ) return

        if (panel.isIncludeSubdirectories().not()) {
            if (Objects.deepEquals(panel.getPermissions(), files.first().second.permissions)) {
                return
            }
        }

        fireActionPerformed(
            ActionEvent(
                ChangePermission(panel.getPermissions(), panel.isIncludeSubdirectories()),
                evt.id,
                evt.actionCommand
            ),
            ActionCommand.ChangePermissions
        )
    }

    private fun newFolderOrNewFile(evt: ActionEvent, actionCommand: ActionCommand) {
        val title = when (actionCommand) {
            ActionCommand.NewFile -> I18n.getString("termora.transport.table.contextmenu.new.file")
            ActionCommand.NewFolder -> I18n.getString("termora.welcome.contextmenu.new.folder.name")
            ActionCommand.Rename -> I18n.getString("termora.transport.table.contextmenu.rename")
            else -> StringUtils.EMPTY
        }
        val defaultValue = if (actionCommand == ActionCommand.Rename) paths.first().name else title
        val text = OptionPane.showInputDialog(owner, title = title, value = defaultValue) ?: return
        if (text.isBlank()) return
        for (i in 0 until model.rowCount) {
            if (model.getPath(i).name == text) {
                OptionPane.showMessageDialog(
                    owner,
                    I18n.getString("termora.transport.file-already-exists", text),
                    messageType = JOptionPane.ERROR_MESSAGE
                )
                return
            }
        }
        fireActionPerformed(ActionEvent(text, evt.id, evt.actionCommand), actionCommand)
    }

    fun addActionListener(listener: ActionListener) {
        eventListeners.add(ActionListener::class.java, listener)
    }

    fun removeActionListener(listener: ActionListener) {
        eventListeners.remove(ActionListener::class.java, listener)
    }

    enum class ActionCommand {
        Transfer,
        Delete,
        Edit,
        Rename,
        NewFolder,
        NewFile,
        Refresh,
        ChangePermissions,
        Rmrf,
        Reconnect,
    }

    data class ChangePermission(val permissions: Set<PosixFilePermission>, val includeSubFolder: Boolean)
}