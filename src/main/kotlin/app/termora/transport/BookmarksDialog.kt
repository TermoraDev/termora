package app.termora.transport

import app.termora.DialogWrapper
import app.termora.DynamicColor
import app.termora.I18n
import app.termora.OptionPane
import com.formdev.flatlaf.util.SystemInfo
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Window
import javax.swing.*
import javax.swing.border.EmptyBorder

class BookmarksDialog(
    owner: Window,
    bookmarks: List<String>
) : DialogWrapper(owner) {

    private val model = DefaultListModel<String>()
    private val list = JList(model)

    private val upBtn = JButton(I18n.getString("termora.transport.bookmarks.up"))
    private val downBtn = JButton(I18n.getString("termora.transport.bookmarks.down"))
    private val deleteBtn = JButton(I18n.getString("termora.remove"))


    init {
        size = Dimension(UIManager.getInt("Dialog.width"), UIManager.getInt("Dialog.height"))
        isModal = true
        title = I18n.getString("termora.transport.bookmarks")

        initView()
        initEvents()

        model.addAll(bookmarks)


        init()
        setLocationRelativeTo(null)
    }

    private fun initView() {

        upBtn.isEnabled = false
        downBtn.isEnabled = false
        deleteBtn.isEnabled = false

        upBtn.isFocusable = false
        downBtn.isFocusable = false
        deleteBtn.isFocusable = false

        list.fixedCellHeight = UIManager.getInt("Tree.rowHeight")
        list.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION

    }

    private fun initEvents() {

        upBtn.addActionListener {
            val rows = list.selectedIndices.sorted()
            list.clearSelection()

            for (row in rows) {
                val a = model.getElementAt(row - 1)
                val b = model.getElementAt(row)
                model.setElementAt(b, row - 1)
                model.setElementAt(a, row)
                list.selectionModel.addSelectionInterval(row - 1, row - 1)
            }
        }

        downBtn.addActionListener {
            val rows = list.selectedIndices.sortedDescending()
            list.clearSelection()

            for (row in rows) {
                val a = model.getElementAt(row + 1)
                val b = model.getElementAt(row)
                model.setElementAt(b, row + 1)
                model.setElementAt(a, row)
                list.selectionModel.addSelectionInterval(row + 1, row + 1)
            }
        }

        deleteBtn.addActionListener {
            if (list.selectionModel.selectedItemsCount > 0) {
                if (OptionPane.showConfirmDialog(
                        SwingUtilities.getWindowAncestor(this),
                        I18n.getString("termora.keymgr.delete-warning"),
                        messageType = JOptionPane.WARNING_MESSAGE
                    ) == JOptionPane.YES_OPTION
                ) {
                    for (e in list.selectionModel.selectedIndices.sortedDescending()) {
                        model.removeElementAt(e)
                    }

                    if (model.size > 0) {
                        list.selectedIndex = 0
                    }
                }
            }
        }


        list.selectionModel.addListSelectionListener {
            upBtn.isEnabled = list.selectionModel.selectedItemsCount > 0
            downBtn.isEnabled = upBtn.isEnabled
            deleteBtn.isEnabled = upBtn.isEnabled

            upBtn.isEnabled = list.minSelectionIndex != 0
            downBtn.isEnabled = list.maxSelectionIndex != model.size - 1
        }
    }

    override fun createCenterPanel(): JComponent {

        val panel = JPanel(BorderLayout())
        panel.add(JScrollPane(list).apply {
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
                .add(upBtn).xy(1, rows).apply { rows += step }
                .add(downBtn).xy(1, rows).apply { rows += step }
                .add(deleteBtn).xy(1, rows).apply { rows += step }
                .build(),
            BorderLayout.EAST)

        panel.border = BorderFactory.createEmptyBorder(
            if (SystemInfo.isWindows || SystemInfo.isLinux) 6 else 0,
            12, 12, 12
        )

        return panel
    }

    override fun createSouthPanel(): JComponent? {
        return null
    }

    fun open(): List<String> {
        isModal = true
        isVisible = true
        return model.elements().toList()
    }

}