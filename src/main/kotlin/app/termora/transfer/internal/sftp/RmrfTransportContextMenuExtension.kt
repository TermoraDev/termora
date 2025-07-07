package app.termora.transfer.internal.sftp

import app.termora.I18n
import app.termora.Icons
import app.termora.OptionPane
import app.termora.WindowScope
import app.termora.transfer.TransportContextMenuExtension
import app.termora.transfer.TransportPopupMenu
import app.termora.transfer.TransportTableModel
import org.apache.sshd.sftp.client.fs.SftpFileSystem
import java.nio.file.FileSystem
import java.nio.file.Path
import javax.swing.JMenuItem
import javax.swing.JOptionPane

internal class RmrfTransportContextMenuExtension private constructor() : TransportContextMenuExtension {
    companion object {
        val instance = RmrfTransportContextMenuExtension()
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

        val rmrfMenu = JMenuItem("rm -rf", Icons.warningIntroduction)
        rmrfMenu.addActionListener {
            if (OptionPane.showConfirmDialog(
                    windowScope.window,
                    I18n.getString("termora.transport.table.contextmenu.rm-warning"),
                    messageType = JOptionPane.ERROR_MESSAGE
                ) == JOptionPane.YES_OPTION
            ) {
                popupMenu.fireActionPerformed(it, TransportPopupMenu.ActionCommand.Rmrf)
            }
        }
        return rmrfMenu
    }

    override fun ordered(): Long {
        return 0
    }
}