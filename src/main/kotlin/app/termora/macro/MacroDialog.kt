package app.termora.macro

import app.termora.*
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import com.formdev.flatlaf.util.SystemInfo
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.jdesktop.swingx.action.ActionManager
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Window
import javax.swing.*
import javax.swing.border.EmptyBorder

@Suppress("DuplicatedCode")
class MacroDialog(owner: Window) : DialogWrapper(owner) {

    private val model = DefaultListModel<Macro>()
    private val list = JList(model)
    private val macroManager by lazy { MacroManager.getInstance() }

    private val runBtn = JButton(I18n.getString("termora.macro.run"))
    private val editBtn = JButton(I18n.getString("termora.keymgr.edit"))
    private val copyBtn = JButton(I18n.getString("termora.copy"))
    private val deleteBtn = JButton(I18n.getString("termora.remove"))


    init {
        size = Dimension(UIManager.getInt("Dialog.width"), UIManager.getInt("Dialog.height"))
        isModal = true
        title = I18n.getString("termora.macro.manager")

        initView()
        initEvents()

        init()
        setLocationRelativeTo(owner)
    }

    private fun initView() {

        copyBtn.isEnabled = false
        runBtn.isEnabled = false
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
            ): Component {
                if (value is Macro) {
                    val color = UIManager.getColor("TextField.placeholderForeground")
                    return super.getListCellRendererComponent(
                        list,
                        "<html>${value.name} <font color=rgb(${color.red},${color.green},${color.blue})>${String(value.macroByteArray)}</font></html>",
                        index,
                        isSelected,
                        cellHasFocus
                    )
                }
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            }
        }
        model.addAll(macroManager.getMacros())


    }

    private fun initEvents() {

        editBtn.addActionListener {
            val index = list.selectedIndex
            if (index >= 0) {
                val macro = model.getElementAt(index)
                val text = OptionPane.showInputDialog(
                    this,
                    title = macro.name,
                    value = macro.name
                ) ?: String()
                if (text.isNotBlank()) {
                    val newMacro = macro.copy(name = text)
                    macroManager.addMacro(newMacro)
                    model.setElementAt(newMacro, index)
                }
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
                        val macro = model.getElementAt(e)
                        model.removeElementAt(e)
                        macroManager.removeMacro(macro.id)
                    }
                }
            }
        }

        runBtn.addActionListener(object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                val windowScope = evt.getData(DataProviders.WindowScope) ?: return
                val index = list.selectedIndex
                if (index >= 0) {
                    val macroAction = ActionManager.getInstance().getAction(Actions.MACRO)
                    if (macroAction is MacroAction) {
                        macroAction.runMacro(windowScope, model.getElementAt(index))
                    }
                }
            }
        })

        copyBtn.addActionListener {
            if (list.selectionModel.selectedItemsCount > 0) {
                val now = System.currentTimeMillis()
                val rows = list.selectionModel.selectedIndices
                for (i in rows.indices) {
                    val m = model.getElementAt(i)
                    val macro = m.copy(
                        id = randomUUID(),
                        name = "${m.name} ${copyBtn.text}",
                        created = now,
                        sort = now + i
                    )
                    model.addElement(macro)
                    macroManager.addMacro(macro)
                }
            }
        }

        list.selectionModel.addListSelectionListener {
            editBtn.isEnabled = list.selectionModel.selectedItemsCount > 0
            deleteBtn.isEnabled = editBtn.isEnabled
            runBtn.isEnabled = editBtn.isEnabled
            copyBtn.isEnabled = editBtn.isEnabled
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
            "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
        )
        panel.add(
            FormBuilder.create().layout(layout).padding(EmptyBorder(0, 12, 0, 0))
                .add(runBtn).xy(1, rows).apply { rows += step }
                .add(copyBtn).xy(1, rows).apply { rows += step }
                .add(editBtn).xy(1, rows).apply { rows += step }
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

}