package app.termora.sftp

import app.termora.I18n
import app.termora.NativeStringComparator
import app.termora.formatBytes
import app.termora.vfs2.FileObjectDescriptor
import app.termora.vfs2.sftp.MySftpFileObject
import com.formdev.flatlaf.util.SystemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.commons.lang3.time.DateFormatUtils
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.FileType
import org.apache.commons.vfs2.provider.local.LocalFileSystem
import org.slf4j.LoggerFactory
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.*
import javax.swing.Icon
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel

class FileSystemViewTableModel : DefaultTableModel() {


    companion object {
        const val COLUMN_NAME = 0
        const val COLUMN_TYPE = 1
        const val COLUMN_FILE_SIZE = 2
        const val COLUMN_LAST_MODIFIED_TIME = 3
        const val COLUMN_ATTRS = 4
        const val COLUMN_OWNER = 5

        private val log = LoggerFactory.getLogger(FileSystemViewTableModel::class.java)

        fun fromSftpPermissions(sftpPermissions: Int): Set<PosixFilePermission> {
            val result = mutableSetOf<PosixFilePermission>()

            // 将十进制权限转换为八进制字符串
            val octalPermissions = sftpPermissions.toString(8)

            // 仅取后三位权限部分
            if (octalPermissions.length < 3) {
                return result
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

    var hasParent: Boolean = false
        private set

    override fun getValueAt(row: Int, column: Int): Any {
        val file = getFileObject(row)
        val isParentRow = hasParent && row == 0

        try {
            if (file.type == FileType.IMAGINARY) return StringUtils.EMPTY
            return when (column) {
                COLUMN_NAME -> if (isParentRow) ".." else file.name.baseName
                COLUMN_FILE_SIZE -> if (isParentRow || file.isFolder) StringUtils.EMPTY else formatBytes(file.content.size)
                COLUMN_TYPE -> if (isParentRow) StringUtils.EMPTY else getFileType(file)
                COLUMN_LAST_MODIFIED_TIME -> if (isParentRow) StringUtils.EMPTY else getLastModifiedTime(file)
                COLUMN_ATTRS -> if (isParentRow) StringUtils.EMPTY else getAttrs(file)
                COLUMN_OWNER -> StringUtils.EMPTY
                else -> StringUtils.EMPTY
            }
        } catch (e: Exception) {
            if (file.fileSystem is LocalFileSystem) {
                if (ExceptionUtils.getRootCause(e) is java.nio.file.NoSuchFileException) {
                    SwingUtilities.invokeLater { removeRow(row) }
                    return StringUtils.EMPTY
                }
            }
            if (log.isWarnEnabled) {
                log.warn(e.message, e)
            }
            return StringUtils.EMPTY
        }
    }

    private fun getFileType(file: FileObject): String {
        if (file is FileObjectDescriptor) {
            val type = file.getTypeDescription()
            if (type != null) return type
        }
        return if (SystemInfo.isWindows) NativeFileIcons.getIcon(file.name.baseName, file.isFile).second
        else if (file.isSymbolicLink) I18n.getString("termora.transport.table.type.symbolic-link")
        else NativeFileIcons.getIcon(file.name.baseName, file.isFile).second
    }

    fun getFileIcon(file: FileObject, width: Int = 16, height: Int = 16): Icon {
        if (file is FileObjectDescriptor) {
            val icon = file.getIcon(width, height)
            if (icon != null) return icon
        }
        return if (SystemInfo.isWindows) NativeFileIcons.getIcon(file.name.baseName, file.isFile, width, height).first
        else NativeFileIcons.getIcon(file.name.baseName, file.isFile).first
    }

    fun getFileIcon(row: Int): Icon {
        return getFileIcon(getFileObject(row))
    }

    fun getLastModifiedTime(file: FileObject): String {
        var lastModified: Long = 0
        if (file is FileObjectDescriptor) {
            val time = file.getLastModified()
            if (time != null) lastModified = time
        } else {
            lastModified = file.content.lastModifiedTime
        }
        if (lastModified < 1) return "-"
        return DateFormatUtils.format(Date(lastModified), "yyyy/MM/dd HH:mm")
    }

    private fun getAttrs(file: FileObject): String {
        if (file.fileSystem is LocalFileSystem) return StringUtils.EMPTY
        return PosixFilePermissions.toString(getFilePermissions(file))
    }

    fun getFilePermissions(file: FileObject): Set<PosixFilePermission> {
        val permissions = file.content.getAttribute(MySftpFileObject.POSIX_FILE_PERMISSIONS)
                as Int? ?: return emptySet()
        return fromSftpPermissions(permissions)
    }

    override fun getDataVector(): Vector<Vector<Any>> {
        return super.getDataVector()
    }

    override fun getColumnCount(): Int {
        return 6
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columnIndex) {
            COLUMN_NAME -> String::class.java
            else -> super.getColumnClass(columnIndex)
        }
    }

    fun getFileObject(row: Int): FileObject {
        return super.getValueAt(row, 0) as FileObject
    }

    fun getPathNames(): Set<String> {
        val names = linkedSetOf<String>()
        for (i in 0 until rowCount) {
            if (hasParent && i == 0) {
                names.add("..")
            } else {
                names.add(getFileObject(i).name.baseName)
            }
        }
        return names
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

    override fun isCellEditable(row: Int, column: Int): Boolean {
        return false
    }

    suspend fun reload(dir: FileObject, useFileHiding: Boolean) {

        if (log.isDebugEnabled) {
            log.debug("Reloading {} , useFileHiding {}", dir, useFileHiding)
        }

        val files = mutableListOf<FileObject>()

        withContext(Dispatchers.IO) {
            dir.refresh()
            for (file in dir.children) {
                if (useFileHiding && file.isHidden) continue
                files.add(file)
            }
        }

        files.sortWith(compareBy<FileObject> { !it.isFolder }.thenComparing { a, b ->
            NativeStringComparator.getInstance().compare(
                a.name.baseName,
                b.name.baseName
            )
        })

        hasParent = dir.parent != null
        if (hasParent) {
            files.addFirst(dir.parent)
        }

        withContext(Dispatchers.Swing) {
            while (rowCount > 0) removeRow(0)
            files.forEach { addRow(arrayOf(it)) }
        }


    }


}