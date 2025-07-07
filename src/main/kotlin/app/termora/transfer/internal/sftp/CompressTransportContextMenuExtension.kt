package app.termora.transfer.internal.sftp

import app.termora.I18n
import app.termora.WindowScope
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.randomUUID
import app.termora.transfer.*
import org.apache.commons.lang3.StringUtils
import org.apache.sshd.common.file.util.MockPath
import org.apache.sshd.sftp.client.fs.SftpFileSystem
import java.nio.file.FileSystem
import java.nio.file.Path
import javax.swing.JMenu
import javax.swing.JMenuItem
import kotlin.io.path.absolutePathString
import kotlin.io.path.name
import kotlin.io.path.pathString

internal class CompressTransportContextMenuExtension private constructor() : TransportContextMenuExtension {
    companion object {
        val instance = CompressTransportContextMenuExtension()
    }

    override fun createJMenuItem(
        windowScope: WindowScope,
        fileSystem: FileSystem?,
        popupMenu: TransportPopupMenu,
        files: List<Pair<Path, TransportTableModel.Attributes>>
    ): JMenuItem {
        if (files.isEmpty() || fileSystem !is SftpFileSystem) throw UnsupportedOperationException()
        val hasParent = files.any { it.second.isParent }
        if (hasParent) throw UnsupportedOperationException()

        val compressMenu = JMenu(I18n.getString("termora.transport.table.contextmenu.compress"))
        for (mode in CompressMode.entries) {
            compressMenu.add(mode.extension).addActionListener(object : AnAction() {
                override fun actionPerformed(evt: AnActionEvent) {
                    compress(evt, fileSystem, mode, files)
                }
            })
        }

        return compressMenu
    }


    private fun compress(
        event: AnActionEvent,
        fileSystem: SftpFileSystem,
        mode: CompressMode,
        files: List<Pair<Path, TransportTableModel.Attributes>>,
    ) {
        val transferManager = event.getData(TransportViewer.MyTransferManager) ?: return
        val file = files.first().first
        val workdir = file.parent ?: file.fileSystem.getPath(file.fileSystem.separator)
        val name = StringUtils.defaultIfBlank(if (files.size > 1) workdir.name else file.name, "compress")
        val target = workdir.resolve(name + ".${mode.extension}")
        val myTransfer = CompressTransfer(fileSystem, mode, files, workdir, target)
        if (transferManager.addTransfer(myTransfer).not()) return

        val panel = event.getData(TransportPanel.MyTransportPanel) ?: return
        transferManager.addTransferListener(object : TransferListener {
            override fun onTransferChanged(transfer: Transfer, state: TransferTreeTableNode.State) {
                if (transfer.id() != myTransfer.id()) return
                if (state == TransferTreeTableNode.State.Done || state == TransferTreeTableNode.State.Failed) {
                    transferManager.removeTransferListener(this)
                    if (state == TransferTreeTableNode.State.Done) {
                        panel.registerSelectRow(target.name)
                    }
                }
            }
        })
    }


    private class CompressTransfer(
        private val fileSystem: SftpFileSystem,
        private val mode: CompressMode,
        private val files: List<Pair<Path, TransportTableModel.Attributes>>,
        private val workdir: Path,
        private val target: Path,
    ) : Transfer, TransferIndeterminate {
        private val myID = randomUUID()
        private var end = false
        private val mySource = if (files.size == 1) files.first().first
        else MockPath(files.joinToString(",") { it.first.pathString })

        @Suppress("CascadeIf")
        override suspend fun transfer(bufferSize: Int): Long {
            if (end) return 0

            val paths = files.joinToString(StringUtils.SPACE) { "'${it.second.name}'" }
            val command = StringBuilder()
            command.append("cd '${workdir.absolutePathString()}'")
            command.append(" && ")
            if (mode == CompressMode.TarGz) {
                command.append("tar -czf")
            } else if (mode == CompressMode.Tar) {
                command.append("tar -cf")
            } else if (mode == CompressMode.Zip) {
                command.append("zip -r")
            } else if (mode == CompressMode.SevenZ) {
                command.append("7z a")
            }
            command.append(" '${target.name}' ")
            command.append(paths)

            fileSystem.clientSession.executeRemoteCommand(command.toString(), System.out, Charsets.UTF_8)

            end = true

            return size()
        }

        override fun source(): Path {
            return mySource
        }

        override fun target(): Path {
            return target
        }

        override fun size(): Long {
            return files.size.toLong()
        }

        override fun isDirectory(): Boolean {
            return false
        }

        override fun priority(): Transfer.Priority {
            return Transfer.Priority.High
        }

        override fun scanning(): Boolean {
            return false
        }

        override fun id(): String {
            return myID
        }

        override fun parentId(): String {
            return StringUtils.EMPTY
        }

    }

    override fun ordered(): Long {
        return 1
    }
}