package app.termora.transport

import app.termora.I18n
import app.termora.formatBytes
import app.termora.formatSeconds
import org.apache.commons.lang3.StringUtils
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel


class FileTransportTableModel(transportManager: TransportManager) : DefaultTableModel() {
    private var isInitialized = false

    private inline fun invokeLater(crossinline block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block.invoke()
        } else {
            SwingUtilities.invokeLater { block.invoke() }
        }
    }

    init {
        transportManager.addTransportListener(object : TransportListener {
            override fun onTransportAdded(transport: Transport) {
                invokeLater { addRow(arrayOf(transport)) }
            }

            override fun onTransportRemoved(transport: Transport) {
                invokeLater {
                    val index = getDataVector().indexOfFirst { it.firstOrNull() == transport }
                    if (index >= 0) {
                        removeRow(index)
                    }
                }
            }

            override fun onTransportChanged(transport: Transport) {
                invokeLater {
                    for ((index, vector) in getDataVector().withIndex()) {
                        if (vector.firstOrNull() == transport) {
                            fireTableRowsUpdated(index, index)
                        }
                    }
                }
            }

        })

        isInitialized = true
    }

    companion object {
        const val COLUMN_NAME = 0
        const val COLUMN_STATUS = 1
        const val COLUMN_PROGRESS = 2
        const val COLUMN_SIZE = 3
        const val COLUMN_SOURCE_PATH = 4
        const val COLUMN_TARGET_PATH = 5
        const val COLUMN_SPEED = 6
        const val COLUMN_ESTIMATED_TIME = 7
    }

    override fun getColumnCount(): Int {
        return 8
    }

    fun getTransport(row: Int): Transport {
        return super.getValueAt(row, COLUMN_NAME) as Transport
    }

    override fun getValueAt(row: Int, column: Int): Any {
        val transport = getTransport(row)
        val isTransporting = transport.state == TransportState.Transporting
        val speed = if (isTransporting) transport.speed else 0
        val estimatedTime = if (isTransporting && speed > 0)
            (transport.size - transport.transferredSize) / speed else 0

        return when (column) {
            COLUMN_NAME -> " ${transport.name}"
            COLUMN_STATUS -> formatStatus(transport.state)
            COLUMN_PROGRESS -> String.format("%.0f%%", transport.progress * 100.0)

            // 大小
            COLUMN_SIZE -> if (transport.size < 0) "-"
            else if (isTransporting) "${formatBytes(transport.transferredSize)}/${formatBytes(transport.size)}"
            else formatBytes(transport.size)

            COLUMN_SOURCE_PATH -> " ${transport.getSourcePath}"
            COLUMN_TARGET_PATH -> " ${transport.getTargetPath}"
            COLUMN_SPEED -> if (isTransporting) formatBytes(speed) else "-"
            COLUMN_ESTIMATED_TIME -> if (isTransporting && speed > 0) formatSeconds(estimatedTime) else "-"
            else -> StringUtils.EMPTY
        }
    }

    private fun formatStatus(state: TransportState): String {
        return when (state) {
            TransportState.Transporting -> I18n.getString("termora.transport.sftp.status.transporting")
            TransportState.Waiting -> I18n.getString("termora.transport.sftp.status.waiting")
            TransportState.Done -> I18n.getString("termora.transport.sftp.status.done")
            TransportState.Failed -> I18n.getString("termora.transport.sftp.status.failed")
            TransportState.Cancelled -> I18n.getString("termora.transport.sftp.status.cancelled")
        }
    }

    override fun getColumnName(column: Int): String {
        return when (column) {
            COLUMN_NAME -> I18n.getString("termora.transport.jobs.table.name")
            COLUMN_STATUS -> I18n.getString("termora.transport.jobs.table.status")
            COLUMN_PROGRESS -> I18n.getString("termora.transport.jobs.table.progress")
            COLUMN_SIZE -> I18n.getString("termora.transport.jobs.table.size")
            COLUMN_SOURCE_PATH -> I18n.getString("termora.transport.jobs.table.source-path")
            COLUMN_TARGET_PATH -> I18n.getString("termora.transport.jobs.table.target-path")
            COLUMN_SPEED -> I18n.getString("termora.transport.jobs.table.speed")
            COLUMN_ESTIMATED_TIME -> I18n.getString("termora.transport.jobs.table.estimated-time")
            else -> StringUtils.EMPTY
        }
    }

    override fun isCellEditable(row: Int, column: Int): Boolean {
        return false
    }
}