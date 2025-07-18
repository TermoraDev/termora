package app.termora.highlight

import app.termora.*
import app.termora.Application.ohMyJson
import app.termora.account.AccountOwner
import app.termora.terminal.TerminalColor
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import com.formdev.flatlaf.extras.components.FlatTable
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.nio.charset.StandardCharsets
import javax.swing.*
import javax.swing.JTabbedPane.SCROLL_TAB_LAYOUT
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

@Suppress("DuplicatedCode")
class KeywordHighlightPanel(private val accountOwner: AccountOwner) : JPanel(BorderLayout()), Disposable {

    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val keywordHighlightManager get() = KeywordHighlightManager.getInstance()
    private val terminal = TerminalFactory.getInstance().createTerminal()
    private val colorPalette get() = terminal.getTerminalModel().getColorPalette()

    private val tabbed = FlatTabbedPane()

    init {
        initView()
        initEvents()
    }

    private fun initView() {

        tabbed.styleMap = mapOf(
            "focusColor" to DynamicColor("TabbedPane.background"),
            "hoverColor" to DynamicColor("TabbedPane.background"),
            "inactiveUnderlineColor" to DynamicColor("TabbedPane.underlineColor"),
        )
        tabbed.isHasFullBorder = false
        tabbed.tabPlacement = JTabbedPane.LEFT
        tabbed.tabType = FlatTabbedPane.TabType.underlined
        tabbed.isTabsClosable = false
        tabbed.tabLayoutPolicy = SCROLL_TAB_LAYOUT
        tabbed.tabWidthMode = FlatTabbedPane.TabWidthMode.preferred
        tabbed.isFocusable = false
        tabbed.tabHeight = UIManager.getInt("TabbedPane.tabHeight") - 8

        val sets = keywordHighlightManager.getKeywordHighlights(accountOwner.id)
            .filter { it.type == KeywordHighlightType.Set }
            .sortedBy { it.sort }

        tabbed.addTab(I18n.getString("termora.highlight.default-set"), KeywordHighlightMiniPanel("0"))

        for (highlight in sets) {
            tabbed.addTab(highlight.keyword, KeywordHighlightMiniPanel(highlight.id))
        }

        add(tabbed, BorderLayout.CENTER)
    }

    private fun initEvents() {

        tabbed.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e).not()) {
                    return
                }

                val index = tabbed.indexAtLocation(e.x, e.y)
                if (index < 0) return
                tabbed.selectedIndex = index

                showContextmenu(index, e)
            }
        })

        Disposer.register(this, object : Disposable {
            override fun dispose() {
                terminal.close()
            }
        })
    }

    private fun showContextmenu(index: Int, e: MouseEvent) {

        var offset = 0
        for (i in 0..index) offset += tabbed.ui.getTabBounds(tabbed, i).height
        val popupMenu = FlatPopupMenu()
        popupMenu.add(I18n.getString("termora.new-host.tunneling.add")).addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val text = OptionPane.showInputDialog(owner) ?: return
                if (text.isBlank()) return
                val highlight = KeywordHighlight(
                    keyword = text,
                    type = KeywordHighlightType.Set
                )
                keywordHighlightManager.addKeywordHighlight(highlight, accountOwner)
                tabbed.addTab(text, KeywordHighlightMiniPanel(highlight.id))
                tabbed.selectedIndex = tabbed.tabCount - 1
            }
        })

        if (index > 0) {
            popupMenu.add(I18n.getString("termora.tabbed.contextmenu.rename"))
                .addActionListener(object : AbstractAction() {
                    override fun actionPerformed(e: ActionEvent) {
                        val tab = tabbed.getComponentAt(index) as? KeywordHighlightMiniPanel ?: return
                        val title = tabbed.getTitleAt(index) ?: return
                        val text = OptionPane.showInputDialog(owner, value = title) ?: return
                        if (text.isBlank() || title == text) return
                        val highlight = keywordHighlightManager.getKeywordHighlights(accountOwner.id)
                            .filter { it.type == KeywordHighlightType.Set }
                            .firstOrNull { it.id == tab.setId } ?: return
                        keywordHighlightManager.addKeywordHighlight(
                            highlight.copy(
                                updateDate = System.currentTimeMillis(),
                                keyword = text
                            ), accountOwner
                        )
                        tabbed.setTitleAt(index, text)
                    }
                })

            popupMenu.add(I18n.getString("termora.remove")).addActionListener(object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    val tab = tabbed.getComponentAt(index) as? KeywordHighlightMiniPanel ?: return

                    if (OptionPane.showConfirmDialog(
                            owner,
                            I18n.getString("termora.keymgr.delete-warning"),
                            I18n.getString("termora.remove"),
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE
                        ) != JOptionPane.YES_OPTION
                    ) return

                    tabbed.removeTabAt(index)

                    for (highlight in keywordHighlightManager.getKeywordHighlights(accountOwner.id)) {
                        if (highlight.parentId == tab.setId || highlight.id == tab.setId) {
                            keywordHighlightManager.removeKeywordHighlight(highlight.id)
                        }
                    }
                }
            })
        }

        popupMenu.show(e.component, e.x, e.y)
    }


    private inner class KeywordHighlightMiniPanel(val setId: String) : JPanel(BorderLayout()) {

        private val addBtn = JButton(I18n.getString("termora.new-host.tunneling.add"))
        private val editBtn = JButton(I18n.getString("termora.keymgr.edit"))
        private val deleteBtn = JButton(I18n.getString("termora.remove"))
        private val importBtn = JButton(I18n.getString("termora.keymgr.import"))
        private val exportBtn = JButton(I18n.getString("termora.keymgr.export"))
        private val model = KeywordHighlightTableModel(accountOwner, setId)
        private val table = FlatTable()

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
                                keywordHighlight.copy(enabled = keywordHighlight.enabled.not()),
                                accountOwner
                            )
                            model.fireTableCellUpdated(row, column)
                        } else if (row < 0) {
                            table.clearSelection()
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
                    keywordHighlightManager.addKeywordHighlight(keywordHighlight.copy(parentId = setId), accountOwner)
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
                        keywordHighlight = value.copy(
                            id = keywordHighlight.id, parentId = setId,
                            sort = keywordHighlight.sort
                        )
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

            exportBtn.addActionListener {
                val fileChooser = FileChooser()
                fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
                fileChooser.win32Filters.add(Pair("All files", listOf("*")))
                fileChooser.showSaveDialog(owner, "highlights.json").thenAccept { file ->
                    file?.outputStream()?.use {
                        val highlights = keywordHighlightManager.getKeywordHighlights(accountOwner.id)
                            .filter { e -> e.parentId == setId }
                            .map { e -> e.copy(id = randomUUID()) }
                        IOUtils.write(ohMyJson.encodeToString(highlights), it, StandardCharsets.UTF_8)
                    }
                }
            }

            importBtn.addActionListener {
                val chooser = FileChooser()
                chooser.osxAllowedFileTypes = listOf("json")
                chooser.allowsMultiSelection = false
                chooser.win32Filters.add(Pair("JSON files", listOf("json")))
                chooser.fileSelectionMode = JFileChooser.FILES_ONLY
                chooser.showOpenDialog(owner)
                    .thenAccept { if (it.isNotEmpty()) SwingUtilities.invokeLater { importKeywordHighlights(it.first()) } }
            }

        }

        private fun importKeywordHighlights(file: File) {
            try {
                val highlights = ohMyJson.decodeFromString<List<KeywordHighlight>>(file.readText())
                    .map { it.copy(id = randomUUID(), parentId = setId) }
                for (highlight in highlights) {
                    keywordHighlightManager.addKeywordHighlight(highlight, accountOwner)
                    model.fireTableRowsInserted(model.rowCount - 1, model.rowCount)
                }
            } catch (e: Exception) {
                OptionPane.showMessageDialog(
                    owner,
                    message = e.message ?: ExceptionUtils.getRootCauseMessage(e),
                    messageType = JOptionPane.ERROR_MESSAGE,
                )
            }
        }


        private fun createCenterPanel(): JComponent {

            val panel = JPanel(BorderLayout())
            panel.add(JScrollPane(table).apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(8, 8, 8, 0),
                    BorderFactory.createMatteBorder(1, 1, 1, 1, DynamicColor.BorderColor)
                )
            }, BorderLayout.CENTER)

            var rows = 1
            val step = 2
            val formMargin = "4dlu"
            val layout = FormLayout(
                "default:grow",
                "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
            )
            panel.add(
                FormBuilder.create().layout(layout)
                    .border(BorderFactory.createEmptyBorder(8, 8, 8, 8))
                    .add(addBtn).xy(1, rows).apply { rows += step }
                    .add(editBtn).xy(1, rows).apply { rows += step }
                    .add(deleteBtn).xy(1, rows).apply { rows += step }
                    .add(importBtn).xy(1, rows).apply { rows += step }
                    .add(exportBtn).xy(1, rows).apply { rows += step }
                    .build(),
                BorderLayout.EAST)

            return panel
        }
    }
}