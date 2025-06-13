package app.termora.keymgr

import javax.swing.table.DefaultTableModel

class KeyPairTableModel() : DefaultTableModel() {

    override fun isCellEditable(row: Int, column: Int): Boolean {
        return false
    }

    fun getOhKeyPair(row: Int): OhKeyPair {
        return super.getValueAt(row, 0) as OhKeyPair
    }

    override fun setValueAt(value: Any, row: Int, column: Int) {
        super.setValueAt(value, row, 0)
    }

    override fun getValueAt(row: Int, column: Int): Any {
        val ohKeyPair = getOhKeyPair(row)
        return when (column) {
            0 -> ohKeyPair.name
            1 -> ohKeyPair.type
            2 -> ohKeyPair.length
            3 -> ohKeyPair.remark
            else -> String()
        }
    }
}