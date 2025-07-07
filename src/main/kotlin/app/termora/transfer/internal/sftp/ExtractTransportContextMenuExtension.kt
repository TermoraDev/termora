package app.termora.transfer.internal.sftp

import app.termora.I18n
import app.termora.WindowScope
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.randomUUID
import app.termora.transfer.*
import org.apache.commons.lang3.StringUtils
import org.apache.sshd.sftp.client.fs.SftpFileSystem
import java.nio.file.FileSystem
import java.nio.file.Path
import javax.swing.JMenu
import javax.swing.JMenuItem
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

internal class ExtractTransportContextMenuExtension private constructor() : TransportContextMenuExtension {
    companion object {
        val instance = ExtractTransportContextMenuExtension()
    }

    override fun createJMenuItem(
        windowScope: WindowScope,
        fileSystem: FileSystem?,
        popupMenu: TransportPopupMenu,
        files: List<Pair<Path, TransportTableModel.Attributes>>
    ): JMenuItem {
        if (files.isEmpty() || fileSystem !is SftpFileSystem) throw UnsupportedOperationException()
        val hasParent = files.any { it.second.isParent || it.second.isDirectory }
        if (hasParent) throw UnsupportedOperationException()

        val map = mutableMapOf<Path, CompressMode>()
        for ((path, attr) in files) {
            val mode = CompressMode.entries.firstOrNull { attr.name.endsWith(".${it.extension}", true) }
            if (mode == null) {
                throw UnsupportedOperationException()
            }
            map[path] = mode
        }

        val extractMenu = JMenu(I18n.getString("termora.transport.table.contextmenu.extract"))
        extractMenu.add(I18n.getString("termora.transport.table.contextmenu.extract.here"))
            .addActionListener(object : AnAction() {
                override fun actionPerformed(evt: AnActionEvent) {
                    extract(evt, fileSystem, ExtractLocation.Here, files.map { it.first to map.getValue(it.first) })
                }
            })
        if (files.size == 1) {
            val first = files.first()
            val name = StringUtils.removeEndIgnoreCase(first.second.name, ".${map.getValue(first.first).extension}")
            val text = I18n.getString("termora.transport.table.contextmenu.extract.single", name)
            extractMenu.add(text).addActionListener(object : AnAction() {
                override fun actionPerformed(evt: AnActionEvent) {
                    extract(evt, fileSystem, ExtractLocation.Self, files.map { it.first to map.getValue(it.first) })
                }
            })
        } else {
            extractMenu.add(I18n.getString("termora.transport.table.contextmenu.extract.multi"))
                .addActionListener(object : AnAction() {
                    override fun actionPerformed(evt: AnActionEvent) {
                        extract(evt, fileSystem, ExtractLocation.Self, files.map { it.first to map.getValue(it.first) })
                    }
                })
        }


        return extractMenu
    }

    private fun extract(
        event: AnActionEvent,
        fileSystem: SftpFileSystem,
        location: ExtractLocation,
        files: List<Pair<Path, CompressMode>>,
    ) {
        for (pair in files) {
            extract(event, fileSystem, location, pair.second, pair.first)
        }
    }


    private fun extract(
        event: AnActionEvent,
        fileSystem: SftpFileSystem,
        location: ExtractLocation,
        mode: CompressMode,
        file: Path,
    ) {
        val transferManager = event.getData(TransportViewer.MyTransferManager) ?: return
        val workdir = file.parent ?: file.fileSystem.getPath(file.fileSystem.separator)
        val target = if (location == ExtractLocation.Self)
            workdir.resolve(StringUtils.removeEndIgnoreCase(file.name, ".${mode.extension}"))
        else workdir
        transferManager.addTransfer(ExtractTransfer(fileSystem, mode, file, workdir, target))
    }

    private class ExtractTransfer(
        private val fileSystem: SftpFileSystem,
        private val mode: CompressMode,
        private val file: Path,
        private val workdir: Path,
        private val target: Path,
    ) : Transfer, TransferIndeterminate {
        private val myID = randomUUID()
        private var end = false

        @Suppress("CascadeIf")
        override suspend fun transfer(bufferSize: Int): Long {
            if (end) return 0

            val command = StringBuilder()
            command.append("cd '${workdir.absolutePathString()}'")
            command.append(" && ")

            if (mode == CompressMode.TarGz || mode == CompressMode.Tar) {
                command.append(" mkdir -p '${target.absolutePathString()}' ")
                command.append(" && ")

                command.append(" tar ")
                if (mode == CompressMode.Tar) {
                    command.append(" -xf ")
                } else {
                    command.append(" -zxf ")
                }

                command.append(" '${source().absolutePathString()}' ")
                command.append(" -C '${target.absolutePathString()}' ")
            } else if (mode == CompressMode.Zip) {
                command.append(" unzip -o -q ")
                command.append(" '${source().absolutePathString()}' ")
                command.append(" -d '${target.absolutePathString()}' ")
            } else if (mode == CompressMode.SevenZ) {
                command.append(" 7z x ")
                command.append(" '${source().absolutePathString()}' ")
                command.append(" -o'${target.absolutePathString()}' -y > /dev/null")
            }

            fileSystem.clientSession.executeRemoteCommand(command.toString(), System.out, Charsets.UTF_8)

            end = true

            return size()
        }

        override fun source(): Path {
            return file
        }

        override fun target(): Path {
            return target
        }

        override fun size(): Long {
            return 1
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


    private enum class ExtractLocation {
        Self,
        Here,
    }

    override fun ordered(): Long {
        return 2
    }
}