package app.termora.highlight

import app.termora.*
import app.termora.account.AccountOwner
import app.termora.terminal.TerminalColor
import com.formdev.flatlaf.extras.components.FlatTable
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

@Suppress("DuplicatedCode")
class KeywordHighlightPanel(private val accountOwner: AccountOwner) : JPanel(BorderLayout()), Disposable {

    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val model = KeywordHighlightTableModel(accountOwner)
    private val table = FlatTable()
    private val keywordHighlightManager get() = KeywordHighlightManager.getInstance()
    private val terminal = TerminalFactory.getInstance().createTerminal()
    private val colorPalette get() = terminal.getTerminalModel().getColorPalette()

    private val addBtn = JButton(I18n.getString("termora.new-host.tunneling.add"))
    private val editBtn = JButton(I18n.getString("termora.keymgr.edit"))
    private val deleteBtn = JButton(I18n.getString("termora.remove"))


    init {
        initView()
        initEvents()
    }

    private fun initView() {
        model.addColumn(I18n.getString("termora.highlight.keyword"))
        model.addColumn(I18n.getString("termora.highlight.preview"))
        model.addColumn(I18n.getString("termora.highlight.description"))
        table.fillsViewportHeight = true
        table.tableHeader.reorderingAllowed = false
        table.model = model

        editBtn.isEnabled = false
        deleteBtn.isEnabled = false

        // keyword
        table.columnModel.getColumn(0).setCellRenderer(object : JCheckBox(), TableCellRenderer {
            init {
                horizontalAlignment = LEFT
                verticalAlignment = CENTER
            }

            override fun getTableCellRendererComponent(
                table: JTable,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                if (value is KeywordHighlight) {
                    text = value.keyword
                    super.setSelected(value.enabled)
                }
                if (isSelected) {
                    foreground = table.selectionForeground
                    super.setBackground(table.selectionBackground)
                } else {
                    foreground = table.foreground
                    background = table.background
                }
                return this
            }

        })

        // preview
        table.columnModel.getColumn(1).setCellRenderer(object : DefaultTableCellRenderer() {
            private val keywordHighlightView = KeywordHighlightView(0)

            init {
                keywordHighlightView.border = null
            }

            override fun getTableCellRendererComponent(
                table: JTable,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                if (value is KeywordHighlight) {
                    keywordHighlightView.setKeywordHighlight(value, colorPalette)
                    if (isSelected) keywordHighlightView.backgroundColor = table.selectionBackground
                    return keywordHighlightView
                }
                return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            }
        })

        add(createCenterPanel(), BorderLayout.CENTER)

    }

    private fun initEvents() {

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    val row = table.rowAtPoint(e.point)
                    val column = table.columnAtPoint(e.point)
                    if (row >= 0 && column == 0) {
                        val keywordHighlight = model.getKeywordHighlight(row)
                        keywordHighlightManager.addKeywordHighlight(
                            keywordHighlight.copy(enabled = !keywordHighlight.enabled),
                            accountOwner
                        )
                        model.fireTableCellUpdated(row, column)
                    }
                }
            }
        })

        addBtn.addActionListener {
            val dialog = NewKeywordHighlightDialog(owner, colorPalette)
            dialog.setLocationRelativeTo(owner)
            dialog.isVisible = true
            val keywordHighlight = dialog.keywordHighlight
            if (keywordHighlight != null) {
                keywordHighlightManager.addKeywordHighlight(keywordHighlight, accountOwner)
                model.fireTableRowsInserted(model.rowCount - 1, model.rowCount)
            }
        }

        editBtn.addActionListener {
            val row = table.selectedRow
            if (row > -1) {
                var keywordHighlight = model.getKeywordHighlight(row)
                val dialog = NewKeywordHighlightDialog(owner, colorPalette)
                dialog.setLocationRelativeTo(owner)
                dialog.keywordTextField.text = keywordHighlight.keyword
                dialog.descriptionTextField.text = keywordHighlight.description

                if (keywordHighlight.textColor <= 16) {
                    if (keywordHighlight.textColor == 0) {
                        dialog.textColor.color = Color(colorPalette.getColor(TerminalColor.Basic.FOREGROUND))
                    } else {
                        dialog.textColor.color = Color(colorPalette.getXTerm256Color(keywordHighlight.textColor))
                    }
                    dialog.textColor.colorIndex = keywordHighlight.textColor
                } else {
                    dialog.textColor.color = Color(keywordHighlight.textColor)
                    dialog.textColor.colorIndex = -1
                }

                if (keywordHighlight.backgroundColor <= 16) {
                    if (keywordHighlight.backgroundColor == 0) {
                        dialog.backgroundColor.color = Color(colorPalette.getColor(TerminalColor.Basic.BACKGROUND))
                    } else {
                        dialog.backgroundColor.color =
                            Color(colorPalette.getXTerm256Color(keywordHighlight.backgroundColor))
                    }
                    dialog.backgroundColor.colorIndex = keywordHighlight.backgroundColor
                } else {
                    dialog.backgroundColor.color = Color(keywordHighlight.backgroundColor)
                    dialog.backgroundColor.colorIndex = -1
                }

                dialog.boldCheckBox.isSelected = keywordHighlight.bold
                dialog.italicCheckBox.isSelected = keywordHighlight.italic
                dialog.underlineCheckBox.isSelected = keywordHighlight.underline
                dialog.lineThroughCheckBox.isSelected = keywordHighlight.lineThrough
                dialog.matchCaseBtn.isSelected = keywordHighlight.matchCase
                dialog.regexBtn.isSelected = keywordHighlight.regex

                dialog.isVisible = true

                val value = dialog.keywordHighlight
                if (value != null) {
                    keywordHighlight = value.copy(id = keywordHighlight.id, sort = keywordHighlight.sort)
                    keywordHighlightManager.addKeywordHighlight(keywordHighlight, accountOwner)
                    model.fireTableRowsUpdated(row, row)
                }
            }

        }

        deleteBtn.addActionListener {
            if (table.selectedRowCount > 0) {
                if (OptionPane.showConfirmDialog(
                        SwingUtilities.getWindowAncestor(this),
                        I18n.getString("termora.keymgr.delete-warning"),
                        messageType = JOptionPane.WARNING_MESSAGE
                    ) == JOptionPane.YES_OPTION
                ) {
                    val rows = table.selectedRows.sorted().reversed()
                    for (row in rows) {
                        val id = model.getKeywordHighlight(row).id
                        keywordHighlightManager.removeKeywordHighlight(id)
                        model.fireTableRowsDeleted(row, row)
                    }
                }
            }
        }

        table.selectionModel.addListSelectionListener {
            editBtn.isEnabled = table.selectedRowCount > 0
            deleteBtn.isEnabled = editBtn.isEnabled
        }

        Disposer.register(this, object : Disposable {
            override fun dispose() {
                terminal.close()
            }
        })
    }

    private fun createCenterPanel(): JComponent {

        val panel = JPanel(BorderLayout())
        panel.add(JScrollPane(table).apply {
            border = BorderFactory.createMatteBorder(1, 1, 1, 1, DynamicColor.BorderColor)
        }, BorderLayout.CENTER)

        var rows = 1
        val step = 2
        val formMargin = "4dlu"
        val layout = FormLayout(
            "default:grow",
            "pref, $formMargin, pref, $formMargin, pref"
        )
        panel.add(
            FormBuilder.create().layout(layout).padding(EmptyBorder(0, 12, 0, 0))
                .add(addBtn).xy(1, rows).apply { rows += step }
                .add(editBtn).xy(1, rows).apply { rows += step }
                .add(deleteBtn).xy(1, rows).apply { rows += step }
                .build(),
            BorderLayout.EAST)

        panel.border = BorderFactory.createEmptyBorder(
            12,
            12, 12, 12
        )

        return panel
    }


}