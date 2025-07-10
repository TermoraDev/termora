package app.termora

import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatTable
import com.formdev.flatlaf.extras.components.FlatToolBar
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Window
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import kotlin.math.max

internal class LoginScriptPanel(private val loginScripts: MutableList<LoginScript>) : JPanel(BorderLayout()) {

    private val owner get() = SwingUtilities.getWindowAncestor(this)

    private val addBtn = JButton(I18n.getString("termora.new-host.tunneling.add"))
    private val editBtn = JButton(I18n.getString("termora.new-host.tunneling.edit"))
    private val deleteBtn = JButton(I18n.getString("termora.new-host.tunneling.delete"))
    private val table = FlatTable()
    private val model = object : DefaultTableModel() {
        override fun getRowCount(): Int {
            return loginScripts.size
        }

        override fun isCellEditable(row: Int, column: Int): Boolean {
            return false
        }

        fun addRow(loginScript: LoginScript) {
            val rowCount = super.getRowCount()
            loginScripts.add(loginScript)
            super.fireTableRowsInserted(rowCount, rowCount + 1)
        }

        override fun getValueAt(row: Int, column: Int): Any {
            val loginScript = loginScripts[row]
            return when (column) {
                0 -> loginScript.expect
                1 -> loginScript.send
                else -> super.getValueAt(row, column)
            }
        }
    }

    init {
        initView()
        initEvents()
    }

    private fun initView() {

        addBtn.isFocusable = false
        editBtn.isFocusable = false
        deleteBtn.isFocusable = false

        deleteBtn.isEnabled = false
        editBtn.isEnabled = false

        val scrollPane = JScrollPane(table)

        model.addColumn(I18n.getString("termora.new-host.terminal.expect"))
        model.addColumn(I18n.getString("termora.new-host.terminal.send"))

        table.putClientProperty(
            FlatClientProperties.STYLE, mapOf(
                "showHorizontalLines" to true,
                "showVerticalLines" to true,
            )
        )
        table.model = model
        table.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        table.setDefaultRenderer(
            Any::class.java,
            DefaultTableCellRenderer().apply { horizontalAlignment = SwingConstants.CENTER })
        table.fillsViewportHeight = true
        scrollPane.border = BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(4, 0, 4, 0),
            BorderFactory.createMatteBorder(1, 1, 1, 1, DynamicColor.Companion.BorderColor)
        )
        table.border = BorderFactory.createEmptyBorder()


        val box = Box.createHorizontalBox()
        box.add(addBtn)
        box.add(Box.createHorizontalStrut(4))
        box.add(editBtn)
        box.add(Box.createHorizontalStrut(4))
        box.add(deleteBtn)

        add(scrollPane, BorderLayout.CENTER)
        add(box, BorderLayout.SOUTH)
        border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
    }


    private fun initEvents() {
        addBtn.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val dialog = LoginScriptDialog(owner)
                dialog.isVisible = true
                model.addRow(dialog.loginScript ?: return)
            }
        })

        editBtn.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val dialog = LoginScriptDialog(owner, loginScripts[table.selectedRow])
                dialog.isVisible = true
                loginScripts[table.selectedRow] = dialog.loginScript ?: return
                model.fireTableRowsUpdated(table.selectedRow, table.selectedRow)
            }
        })

        deleteBtn.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val rows = table.selectedRows
                if (rows.isEmpty()) return
                rows.sortDescending()
                for (row in rows) {
                    loginScripts.removeAt(row)
                    model.fireTableRowsDeleted(row, row)
                }
            }
        })

        table.selectionModel.addListSelectionListener {
            deleteBtn.isEnabled = table.selectedRowCount > 0
            editBtn.isEnabled = deleteBtn.isEnabled
        }
    }

    private inner class LoginScriptDialog(
        owner: Window,
        var loginScript: LoginScript? = null
    ) : DialogWrapper(owner) {
        private val formMargin = "4dlu"
        private val expectTextField = OutlineTextField()
        private val sendTextField = OutlineTextField()
        private val regexToggleBtn = JToggleButton(Icons.regex)
            .apply { toolTipText = I18n.getString("termora.regex") }
        private val matchCaseToggleBtn = JToggleButton(Icons.matchCase)
            .apply { toolTipText = I18n.getString("termora.match-case") }

        init {
            isModal = true
            title = I18n.getString("termora.new-host.terminal.login-scripts")
            controlsVisible = false

            init()
            pack()
            size = Dimension(max(UIManager.getInt("Dialog.width") - 300, 250), preferredSize.height)
            setLocationRelativeTo(owner)

            val toolbar = FlatToolBar().apply { isFloatable = false }
            toolbar.add(regexToggleBtn)
            toolbar.add(matchCaseToggleBtn)
            expectTextField.trailingComponent = toolbar
            expectTextField.placeholderText = I18n.getString("termora.optional")

            val script = loginScript
            if (script != null) {
                expectTextField.text = script.expect
                sendTextField.text = script.send
                matchCaseToggleBtn.isSelected = script.matchCase
                regexToggleBtn.isSelected = script.regex
            }
        }

        override fun doOKAction() {
            if (sendTextField.text.isBlank()) {
                sendTextField.outline = "error"
                sendTextField.requestFocusInWindow()
                return
            }

            loginScript = LoginScript(
                expect = expectTextField.text,
                send = sendTextField.text,
                matchCase = matchCaseToggleBtn.isSelected,
                regex = regexToggleBtn.isSelected,
            )

            super.doOKAction()
        }

        override fun doCancelAction() {
            loginScript = null
            super.doCancelAction()
        }

        override fun createCenterPanel(): JComponent {
            val layout = FormLayout(
                "left:pref, $formMargin, default:grow",
                "pref, $formMargin, pref"
            )

            var rows = 1
            val step = 2
            return FormBuilder.create().layout(layout).padding("0dlu, $formMargin, $formMargin, $formMargin")
                .add("${I18n.getString("termora.new-host.terminal.expect")}:").xy(1, rows)
                .add(expectTextField).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.new-host.terminal.send")}:").xy(1, rows)
                .add(sendTextField).xy(3, rows).apply { rows += step }
                .build()
        }


    }

}