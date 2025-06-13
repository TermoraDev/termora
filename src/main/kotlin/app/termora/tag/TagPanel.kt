package app.termora.tag

import app.termora.*
import app.termora.account.AccountOwner
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*
import javax.swing.border.EmptyBorder

class TagPanel(accountOwner: AccountOwner) : JPanel(BorderLayout()), Disposable {

    private val owner get() = SwingUtilities.getWindowAncestor(this)

    private val model = TagListModel(accountOwner)
    private val list = JList(model)
    private val addBtn = JButton(I18n.getString("termora.new-host.tunneling.add"))
    private val editBtn = JButton(I18n.getString("termora.keymgr.edit"))
    private val deleteBtn = JButton(I18n.getString("termora.remove"))


    init {
        initView()
        initEvents()
    }

    private fun initView() {

        editBtn.isEnabled = false
        deleteBtn.isEnabled = false

        list.fixedCellHeight = UIManager.getInt("Tree.rowHeight")
        list.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        list.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component? {
                val text = if (value is Tag) value.text else value
                val c = super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
                if (value !is Tag) return c

                icon = ColorIcon(color = ColorHash.hash(value.id))

                return c
            }
        }


        add(createCenterPanel(), BorderLayout.CENTER)

    }

    private fun initEvents() {

        addBtn.addActionListener {
            val text = OptionPane.showInputDialog(
                owner,
                title = I18n.getString("termora.tag"),
            )
            if (text.isNullOrBlank().not()) {
                model.addElement(Tag(id = randomUUID(), text = text))
            }
        }

        editBtn.addActionListener {
            val index = list.selectedIndex
            if (index >= 0) {
                val tag = model.getElementAt(index)
                val text = OptionPane.showInputDialog(
                    owner, value = tag.text,
                    title = I18n.getString("termora.tag"),
                )
                if (text.isNullOrBlank().not()) {
                    model.setElementAt(tag.copy(text = text, updateDate = System.currentTimeMillis()), index)
                }
            }
        }

        deleteBtn.addActionListener {
            if (OptionPane.showConfirmDialog(
                    SwingUtilities.getWindowAncestor(this),
                    I18n.getString("termora.keymgr.delete-warning"),
                    messageType = JOptionPane.WARNING_MESSAGE
                ) == JOptionPane.YES_OPTION
            ) {
                for (tag in list.selectedIndices.map { model.getElementAt(it) }) {
                    model.removeElement(tag)
                }
            }
        }

        list.selectionModel.addListSelectionListener {
            editBtn.isEnabled = list.selectedIndices.isNotEmpty()
            deleteBtn.isEnabled = editBtn.isEnabled
        }

    }

    private fun createCenterPanel(): JComponent {

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