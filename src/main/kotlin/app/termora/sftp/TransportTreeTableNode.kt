package app.termora.sftp

import app.termora.I18n
import app.termora.formatBytes
import app.termora.formatSeconds
import app.termora.vfs2.sftp.MySftpFileSystem
import org.apache.commons.vfs2.FileObject
import org.eclipse.jgit.internal.transport.sshd.JGitClientSession
import org.jdesktop.swingx.treetable.DefaultMutableTreeTableNode

class TransportTreeTableNode(transport: Transport) : DefaultMutableTreeTableNode(transport) {
    val transport get() = userObject as Transport

    override fun getValueAt(column: Int): Any {
        val isProcessing = transport.status == TransportStatus.Processing
        val speed = if (isProcessing) transport.speed else 0
        val estimatedTime = if (isProcessing && speed > 0)
            (transport.filesize.get() - transport.transferredFilesize.get()) / speed else 0

        return when (column) {
            TransportTableModel.COLUMN_NAME -> transport.source.name.baseName
            TransportTableModel.COLUMN_STATUS -> formatStatus(transport)
            TransportTableModel.COLUMN_SIZE -> size()
            TransportTableModel.COLUMN_SPEED -> if (isProcessing) formatBytes(speed) + "/s" else "-"
            TransportTableModel.COLUMN_ESTIMATED_TIME -> if (isProcessing) formatSeconds(estimatedTime) else "-"
            TransportTableModel.COLUMN_SOURCE_PATH -> formatPath(transport.source)
            TransportTableModel.COLUMN_TARGET_PATH -> formatPath(transport.target)
            else -> super.getValueAt(column)
        }
    }

    private fun formatPath(file: FileObject): String {
        val fileSystem = file.fileSystem
        if (fileSystem is MySftpFileSystem) {
            val session = fileSystem.getClientSession() as JGitClientSession
            val hostname = session.hostConfigEntry.hostName
            return hostname + ":" + file.name.path
        }
        return file.name.toString()
    }

    private fun formatStatus(transport: Transport): String {
        return when (transport.status) {
            TransportStatus.Processing -> I18n.getString("termora.transport.sftp.status.transporting")
            TransportStatus.Ready -> I18n.getString("termora.transport.sftp.status.waiting")
            TransportStatus.Done -> I18n.getString("termora.transport.sftp.status.done")
            TransportStatus.Failed -> I18n.getString("termora.transport.sftp.status.failed") + ": " + transport.exception.message
        }
    }

    private fun size(): String {
        val transferredFilesize = transport.transferredFilesize.get()
        val filesize = transport.filesize.get()
        if (transferredFilesize <= 0) return formatBytes(filesize)
        return "${formatBytes(transferredFilesize)}/${formatBytes(filesize)}"
    }

    override fun getColumnCount(): Int {
        return TransportTableModel.COLUMN_COUNT
    }

    fun visit(consumer: (TransportTreeTableNode) -> Unit) {
        if (childCount == 0) return
        for (child in children()) {
            if (child is TransportTreeTableNode) {
                child.visit(consumer)
                consumer.invoke(child)
            }
        }
    }

}