package app.termora.highlight

import app.termora.account.AccountOwner
import javax.swing.table.DefaultTableModel

class KeywordHighlightTableModel(private val accountOwner: AccountOwner) : DefaultTableModel() {
    private val rows get() = KeywordHighlightManager.getInstance().getKeywordHighlights(accountOwner.id)

    override fun isCellEditable(row: Int, column: Int): Boolean {
        return false
    }

    fun getKeywordHighlight(row: Int): KeywordHighlight {
        return rows[row]
    }

    @Suppress("SENSELESS_COMPARISON")
    override fun getRowCount(): Int {
        if (accountOwner == null) return 0
        return rows.size
    }

    override fun getValueAt(row: Int, column: Int): Any {
        val highlight = getKeywordHighlight(row)
        return when (column) {
            0 -> highlight
            1 -> highlight
            2 -> highlight.description
            else -> String()
        }
    }
}