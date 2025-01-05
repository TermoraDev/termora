package app.termora.transport

import app.termora.I18n
import app.termora.formatBytes
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.lang3.time.DateFormatUtils
import org.apache.sshd.sftp.client.fs.SftpFileSystem
import org.apache.sshd.sftp.client.fs.SftpPath
import org.slf4j.LoggerFactory
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.*
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel
import kotlin.io.path.*


class FileSystemTableModel(private val fileSystem: FileSystem) : DefaultTableModel() {


    companion object {
        const val COLUMN_NAME = 0
        const val COLUMN_TYPE = 1
        const val COLUMN_FILE_SIZE = 2
        const val COLUMN_LAST_MODIFIED_TIME = 3
        const val COLUMN_ATTRS = 4
        const val COLUMN_OWNER = 5
    }

    private val root = fileSystem.rootDirectories.first()

    var workdir: Path = if (fileSystem is SftpFileSystem) fileSystem.defaultDir
    else fileSystem.getPath(SystemUtils.USER_HOME)
        private set

    @Volatile
    private var files: MutableList<CacheablePath>? = null
    private val propertyChangeListeners = mutableListOf<PropertyChangeListener>()

    val isLocalFileSystem by lazy { FileSystems.getDefault() == fileSystem }

    override fun getRowCount(): Int {
        return files?.size ?: 0
    }

    override fun getValueAt(row: Int, column: Int): Any {
        val path = files?.get(row) ?: return StringUtils.EMPTY

        if (path.fileName == ".." && column != 0) {
            return StringUtils.EMPTY
        }

        return try {
            when (column) {
                COLUMN_NAME -> path
                COLUMN_FILE_SIZE -> if (path.isDirectory) StringUtils.EMPTY else formatBytes(path.fileSize)
                COLUMN_TYPE -> if (path.isDirectory) I18n.getString("termora.transport.table.type.folder") else path.extension
                COLUMN_LAST_MODIFIED_TIME -> DateFormatUtils.format(Date(path.lastModifiedTime), "yyyy/MM/dd HH:mm")

                // 如果是本地的并且还是Windows系统
                COLUMN_ATTRS -> if (isLocalFileSystem && SystemUtils.IS_OS_WINDOWS) StringUtils.EMPTY else PosixFilePermissions.toString(
                    path.posixFilePermissions
                )

                COLUMN_OWNER -> path.owner
                else -> StringUtils.EMPTY
            }
        } catch (e: Exception) {
            StringUtils.EMPTY
        }
    }

    override fun getColumnCount(): Int {
        return 6
    }

    override fun getColumnName(column: Int): String {
        return when (column) {
            COLUMN_NAME -> I18n.getString("termora.transport.table.filename")
            COLUMN_FILE_SIZE -> I18n.getString("termora.transport.table.size")
            COLUMN_TYPE -> I18n.getString("termora.transport.table.type")
            COLUMN_LAST_MODIFIED_TIME -> I18n.getString("termora.transport.table.modified-time")
            COLUMN_ATTRS -> I18n.getString("termora.transport.table.permissions")
            COLUMN_OWNER -> I18n.getString("termora.transport.table.owner")
            else -> StringUtils.EMPTY
        }
    }

    fun getPath(index: Int): Path {
        return getCacheablePath(index).path
    }

    fun getCacheablePath(index: Int): CacheablePath {
        return files?.get(index) ?: throw IndexOutOfBoundsException()
    }

    override fun isCellEditable(row: Int, column: Int): Boolean {
        return false
    }

    override fun removeRow(row: Int) {
        files?.removeAt(row) ?: return
        fireTableRowsDeleted(row, row)
    }

    fun reload() {
        val files = mutableListOf<CacheablePath>()
        if (root != workdir) {
            files.add(CacheablePath(workdir.resolve("..")))
        }

        Files.list(workdir).use {
            for (path in it) {
                if (path is SftpPath) {
                    files.add(SftpCacheablePath(path))
                } else {
                    files.add(CacheablePath(path))
                }
            }
        }
        files.sortWith(compareBy({ !it.isDirectory }, { it.fileName }))

        SwingUtilities.invokeLater {
            this.files = files
            fireTableDataChanged()
        }
    }

    fun workdir(absolutePath: String) {
        workdir(fileSystem.getPath(absolutePath))
    }

    fun workdir(path: Path) {
        this.workdir = path.toAbsolutePath().normalize()
        propertyChangeListeners.forEach {
            it.propertyChange(
                PropertyChangeEvent(
                    this,
                    "workdir",
                    this.workdir,
                    this.workdir
                )
            )
        }
    }

    fun addPropertyChangeListener(propertyChangeListener: PropertyChangeListener) {
        propertyChangeListeners.add(propertyChangeListener)
    }

    open class CacheablePath(val path: Path) {
        val fileName by lazy { path.fileName.toString() }
        val extension by lazy { path.extension }

        open val isDirectory by lazy { path.isDirectory() }
        open val fileSize by lazy { path.fileSize() }
        open val lastModifiedTime by lazy { Files.getLastModifiedTime(path).toMillis() }
        open val owner by lazy { path.getOwner().toString() }
        open val posixFilePermissions by lazy {
            kotlin.runCatching { path.getPosixFilePermissions() }.getOrElse { emptySet() }
        }
    }

    class SftpCacheablePath(sftpPath: SftpPath) : CacheablePath(sftpPath) {
        private val attributes = sftpPath.attributes

        companion object {
            private val log = LoggerFactory.getLogger(SftpCacheablePath::class.java)
            private fun fromSftpPermissions(sftpPermissions: Int): Set<PosixFilePermission> {
                val result = mutableSetOf<PosixFilePermission>()

                // 将十进制权限转换为八进制字符串
                val octalPermissions = sftpPermissions.toString(8)

                // 仅取后三位权限部分
                if (octalPermissions.length < 3) {
                    if (log.isErrorEnabled) {
                        log.error("Invalid permission value: {}", sftpPermissions)
                        return result
                    }
                }

                val permissionBits = octalPermissions.takeLast(3)

                // 解析每一部分的权限
                val owner = permissionBits[0].digitToInt()
                val group = permissionBits[1].digitToInt()
                val others = permissionBits[2].digitToInt()

                // 处理所有者权限
                if ((owner and 4) != 0) result.add(PosixFilePermission.OWNER_READ)
                if ((owner and 2) != 0) result.add(PosixFilePermission.OWNER_WRITE)
                if ((owner and 1) != 0) result.add(PosixFilePermission.OWNER_EXECUTE)

                // 处理组权限
                if ((group and 4) != 0) result.add(PosixFilePermission.GROUP_READ)
                if ((group and 2) != 0) result.add(PosixFilePermission.GROUP_WRITE)
                if ((group and 1) != 0) result.add(PosixFilePermission.GROUP_EXECUTE)

                // 处理其他用户权限
                if ((others and 4) != 0) result.add(PosixFilePermission.OTHERS_READ)
                if ((others and 2) != 0) result.add(PosixFilePermission.OTHERS_WRITE)
                if ((others and 1) != 0) result.add(PosixFilePermission.OTHERS_EXECUTE)

                return result
            }
        }

        override val isDirectory: Boolean
            get() = attributes.isDirectory

        override val fileSize: Long
            get() = attributes.size

        override val lastModifiedTime: Long
                by lazy { attributes.modifyTime.toMillis() }

        override val owner: String
            get() = attributes.owner

        override val posixFilePermissions: Set<PosixFilePermission>
                by lazy { fromSftpPermissions(attributes.permissions) }
    }

}