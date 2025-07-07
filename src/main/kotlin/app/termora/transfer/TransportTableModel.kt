package app.termora.transfer

import app.termora.I18n
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import javax.swing.table.DefaultTableModel

internal class TransportTableModel() : DefaultTableModel() {
    companion object {
        const val COLUMN_NAME = 0
        const val COLUMN_TYPE = 1
        const val COLUMN_FILE_SIZE = 2
        const val COLUMN_LAST_MODIFIED_TIME = 3
        const val COLUMN_ATTRS = 4
        const val COLUMN_OWNER = 5
    }

    override fun getColumnCount(): Int {
        return 6
    }

    fun getPath(row: Int): Path {
        return super.getValueAt(row, 0) as Path
    }

    fun getAttributes(row: Int): Attributes {
        return super.getValueAt(row, 1) as Attributes
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return Attributes::class.java
    }

    override fun getValueAt(row: Int, column: Int): Any? {
        return getAttributes(row)
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

    fun clear() {
        while (rowCount > 0) {
            removeRow(rowCount - 1)
        }
    }


    internal data class Attributes(
        val name: String,
        val type: String,
        val isDirectory: Boolean,
        val isFile: Boolean,
        val isSymbolicLink: Boolean,
        val fileSize: Long,
        val permissions: Set<PosixFilePermission>,
        val owner: String,
        val lastModifiedTime: Long
    ) {
        companion object {
            fun computeType(isSymbolicLink: Boolean, isDirectory: Boolean, name: String): String {
                if (isSymbolicLink) {
                    return I18n.getString("termora.transport.table.type.symbolic-link")
                } else if (isDirectory) {
                    return I18n.getString("termora.folder")
                }
                if (name == "..") return StringUtils.EMPTY
                try {
                    return FilenameUtils.getExtension(name)
                } catch (_: Exception) { // 如果 name 中包含 : 会报错
                    val idx = name.lastIndexOf('.')
                    if (idx < 0) return StringUtils.EMPTY
                    return name.substring(idx + 1)
                }
            }
        }

        val isParent get() = name == ".."
    }

}