package app.termora.transfer

import app.termora.I18n
import app.termora.formatBytes
import app.termora.formatSeconds
import app.termora.transfer.TransferTableModel.Companion.COLUMN_COUNT
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.jdesktop.swingx.treetable.DefaultMutableTreeTableNode
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.absolutePathString
import kotlin.io.path.name
import kotlin.math.max

class TransferTreeTableNode(transfer: Transfer) : DefaultMutableTreeTableNode(transfer) {

    enum class State {
        Ready,
        Processing,
        Failed,
        Done,
    }

    /**
     * 文件总大小，删除时文件夹也算数量
     */
    val filesize = AtomicLong(transfer.size())

    /**
     * 文件传输的大小
     */
    val transferred = AtomicLong(0)

    /**
     * 速率计数
     */
    val counter = TransferTableModel.SlidingWindowByteCounter()

    /**
     * 状态
     */
    private var state = State.Ready

    private var exception: Exception? = null


    override fun getColumnCount(): Int {
        return COLUMN_COUNT
    }

    val transfer get() = getUserObject() as Transfer

    override fun getValueAt(column: Int): Any? {
        val filesize = if (transfer.isDirectory()) filesize.get() else transfer.size()
        val totalBytesTransferred = transferred.get()
        val state = if (waitingChildrenCompleted()) State.Processing else state()
        val isProcessing = state == State.Processing ||
                (transfer is DeleteTransfer && transfer.isDirectory() && (state() == State.Processing || state() == State.Ready))
        val speed = counter.getLastSecondBytes()
        val estimatedTime = max(if (isProcessing && speed > 0) (filesize - totalBytesTransferred) / speed else 0, 0)
        val indeterminate = transfer is TransferIndeterminate
        val formatSize = "${formatBytes(totalBytesTransferred)} / ${formatBytes(filesize)}"
        val formatEstimatedTime = if (indeterminate) "-" else if (isProcessing) formatSeconds(estimatedTime) else "-"

        return when (column) {
            TransferTableModel.COLUMN_NAME -> transfer.source().name
            TransferTableModel.COLUMN_STATUS -> formatStatus(state)
            TransferTableModel.COLUMN_SOURCE_PATH -> formatPath(transfer.source(), false)
            TransferTableModel.COLUMN_TARGET_PATH -> formatPath(transfer.target(), true)
            TransferTableModel.COLUMN_SIZE -> if (indeterminate) "-" else formatSize
            TransferTableModel.COLUMN_SPEED -> if (indeterminate) "-" else if (isProcessing) "${formatBytes(speed)}/s" else "-"
            TransferTableModel.COLUMN_ESTIMATED_TIME -> formatEstimatedTime
            TransferTableModel.COLUMN_PROGRESS -> this
            else -> StringUtils.EMPTY
        }
    }

    private fun formatPath(path: Path, target: Boolean): String {
        if (target) {
            when (transfer) {
                is DeleteTransfer -> return I18n.getString("termora.transport.sftp.status.deleting")
                is CommandTransfer -> return (transfer as CommandTransfer).command
                is ChangePermissionTransfer -> {
                    val permissions = (transfer as ChangePermissionTransfer).permissions
                    // @formatter:off
                    return "${I18n.getString("termora.transport.table.permissions")} -> ${PosixFilePermissions.toString(permissions)}"
                    // @formatter:on
                }
            }
        }

        if (path.fileSystem == FileSystems.getDefault()) {
            return path.absolutePathString()
        }

        return path.absolutePathString()
    }

    private fun formatStatus(state: State): String {
        if ((transfer is DeleteTransfer || transfer is CommandTransfer) && state == State.Processing) {
            return I18n.getString("termora.transport.sftp.status.deleting")
        }

        if (state == State.Failed) {
            val e = exception
            if (e != null) {
                val message = ExceptionUtils.getRootCauseMessage(e)
                return "${I18n.getString("termora.transport.sftp.status.failed")}: $message"
            }
        }

        return when (state) {
            State.Processing -> I18n.getString("termora.transport.sftp.status.transporting")
            State.Ready -> I18n.getString("termora.transport.sftp.status.waiting")
            State.Done -> I18n.getString("termora.transport.sftp.status.done")
            State.Failed -> I18n.getString("termora.transport.sftp.status.failed")
        }
    }


    fun state(): State {
        return state
    }

    /**
     * 等待子完成
     */
    fun waitingChildrenCompleted(): Boolean {
        if (transfer.isDirectory().not()) return false
        if (state == State.Processing) return true
        return state == State.Done && (transfer.scanning() || childCount > 0)
    }

    fun changeState(state: State) {
        if (this.state == State.Done || this.state == State.Failed) {
            throw IllegalStateException()
        }

        if (this.state == State.Processing && state == State.Ready) {
            throw IllegalStateException()
        }

        this.state = state
    }

    fun setException(e: Exception) {
        this.exception = e
    }

    fun tryChangeState(state: State): Boolean {
        if (this.state == State.Done || this.state == State.Failed) {
            return false
        }

        if (this.state == State.Processing && state == State.Ready) {
            return false
        }

        this.state = state

        return true
    }

}