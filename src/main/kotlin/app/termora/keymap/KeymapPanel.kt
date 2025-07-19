package app.termora.keymap

import app.termora.DynamicColor
import app.termora.I18n
import app.termora.Icons
import app.termora.OptionPane
import app.termora.actions.ActionManager
import app.termora.actions.SwitchTabAction
import app.termora.database.DatabaseManager
import app.termora.keymap.KeyShortcut.Companion.toHumanText
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatToolBar
import java.awt.BorderLayout
import java.awt.event.InputEvent
import java.awt.event.ItemEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer


class KeymapPanel : JPanel(BorderLayout()) {

    private val model = KeymapTableModel()
    private val table = JTable(model)
    private val keymapManager get() = KeymapManager.getInstance()
    private val keymapModel = DefaultComboBoxModel<String>()
    private val keymapComboBox = JComboBox(keymapModel)
    private val copyBtn = JButton(Icons.copy)
    private val renameBtn = JButton(Icons.edit)
    private val deleteBtn = JButton(Icons.delete)
    private val database get() = DatabaseManager.getInstance()
    private val allowKeyCodes = mutableSetOf<Int>()

    init {
        initView()
        initEvents()

        // select active
        keymapComboBox.selectedItem = null
        keymapComboBox.selectedItem = keymapManager.getActiveKeymap().name
    }


    private fun initView() {

        for (i in KeyEvent.VK_0..KeyEvent.VK_Z) {
            allowKeyCodes.add(i)
        }

        allowKeyCodes.add(KeyEvent.VK_EQUALS)
        allowKeyCodes.add(KeyEvent.VK_MINUS)
        // https://github.com/TermoraDev/termora/issues/902
        allowKeyCodes.add(KeyEvent.VK_PLUS)


        copyBtn.toolTipText = I18n.getString("termora.welcome.contextmenu.copy")
        renameBtn.toolTipText = I18n.getString("termora.welcome.contextmenu.rename")
        deleteBtn.toolTipText = I18n.getString("termora.remove")

        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

        table.setDefaultRenderer(
            Any::class.java,
            DefaultTableCellRenderer().apply {
                horizontalAlignment = SwingConstants.CENTER
            }
        )

        table.putClientProperty(
            FlatClientProperties.STYLE, mapOf(
                "showHorizontalLines" to true,
                "showVerticalLines" to true,
            )
        )

        val scrollPane = JScrollPane(table)
        scrollPane.border = BorderFactory.createMatteBorder(1, 1, 1, 1, DynamicColor.BorderColor)

        table.background = UIManager.getColor("window")

        for (keymap in keymapManager.getKeymaps()) {
            keymapModel.addElement(keymap.name)
        }

        val box = FlatToolBar()
        box.add(keymapComboBox)
        box.add(Box.createHorizontalStrut(2))
        box.add(copyBtn)
        box.add(renameBtn)
        box.add(deleteBtn)
        box.add(Box.createHorizontalGlue())
        box.border = BorderFactory.createEmptyBorder(0, 0, 6, 0)

        add(box, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun initEvents() {
        table.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                val row = table.selectedRow
                if (row < 0) return
                recordKeyShortcut(row, e)
            }
        })

        copyBtn.addActionListener {
            val keymap = getCurrentKeymap()
            if (keymap != null) {
                copyKeymap(keymap)
            }
        }

        keymapComboBox.addItemListener {
            if (it.stateChange == ItemEvent.SELECTED && keymapComboBox.selectedItem != null) {
                deleteBtn.isEnabled = !(getCurrentKeymap()?.isReadonly ?: true)
                renameBtn.isEnabled = deleteBtn.isEnabled
                database.properties.putString("Keymap.Active", keymapComboBox.selectedItem as String)
                model.fireTableDataChanged()
            }
        }

        renameBtn.addActionListener {
            val keymap = getCurrentKeymap()
            val index = keymapComboBox.selectedIndex
            if (keymap != null && !keymap.isReadonly && index >= 0) {
                val text = OptionPane.showInputDialog(
                    SwingUtilities.getWindowAncestor(this@KeymapPanel),
                    title = renameBtn.toolTipText,
                    value = keymap.name
                )
                if (!text.isNullOrBlank()) {
                    if (text != keymap.name) {
                        keymapManager.removeKeymap(keymap.id)
                        val newKeymap = cloneKeymap(text, keymap)
                        keymapManager.addKeymap(newKeymap)
                        keymapModel.removeElementAt(index)
                        keymapModel.insertElementAt(text, index)
                        keymapModel.selectedItem = newKeymap.name
                    }
                }
            }
        }


        deleteBtn.addActionListener {
            val keymap = getCurrentKeymap()
            val index = keymapComboBox.selectedIndex
            if (keymap != null && !keymap.isReadonly && index >= 0) {
                if (OptionPane.showConfirmDialog(
                        SwingUtilities.getWindowAncestor(this),
                        I18n.getString("termora.keymgr.delete-warning"),
                        messageType = JOptionPane.WARNING_MESSAGE
                    ) == JOptionPane.YES_OPTION
                ) {
                    keymapManager.removeKeymap(keymap.id)
                    keymapModel.removeElementAt(index)
                }
            }
        }
    }


    private fun copyKeymap(keymap: Keymap) {
        var name = keymap.name + " Copy"
        for (i in 0 until Int.MAX_VALUE) {
            if (keymapManager.getKeymap(name) == null) {
                break
            }
            name = keymap.name + " Copy(${i + 1})"
        }

        keymapManager.addKeymap(cloneKeymap(name, keymap))

        keymapModel.insertElementAt(name, 0)
        keymapComboBox.selectedItem = name
    }

    private fun cloneKeymap(name: String, keymap: Keymap): Keymap {
        val newKeymap = Keymap(name, null, false)
        for (e in keymap.getShortcuts()) {
            for (actionId in e.value) {
                newKeymap.addShortcut(actionId, e.key)
            }
        }
        return newKeymap
    }

    private fun getCurrentKeymap(): Keymap? {
        return keymapManager.getKeymap(keymapComboBox.selectedItem as String)
    }

    private fun recordKeyShortcut(row: Int, e: KeyEvent) {
        val action = model.getAction(row) ?: return
        val actionId = (action.getValue(Action.ACTION_COMMAND_KEY) ?: return).toString()
        val keyStroke = KeyStroke.getKeyStrokeForEvent(e)

        // 如果是选择Tab
        if (actionId == SwitchTabAction.SWITCH_TAB && keyStroke.keyCode != KeyEvent.VK_BACK_SPACE) {
            // 如果是 Tab ，那么 keyCode 必须是功能键
            if (keyStroke.keyCode != KeyEvent.VK_META
                && keyStroke.keyCode != KeyEvent.VK_SHIFT
                && keyStroke.keyCode != KeyEvent.VK_CONTROL
                && keyStroke.keyCode != KeyEvent.VK_ALT
            ) {
                return
            }
        } else if (!isCombinationKey(keyStroke) && keyStroke.keyCode == KeyEvent.VK_BACK_SPACE) {
            // ignore
        } else if (!isCombinationKey(keyStroke) || (!allowKeyCodes.contains(keyStroke.keyCode))) {
            return
        }


        var keymap = getCurrentKeymap() ?: return
        if (keymap.isReadonly) {
            copyKeymap(keymap)
            keymap = getCurrentKeymap() ?: return
        }

        e.consume()

        val keyShortcut = KeyShortcut(keyStroke)
        if (e.keyCode == KeyEvent.VK_BACK_SPACE) {
            keymap.removeAllActionShortcuts(actionId)
        } else {
            val actionIds = keymap.getActionIds(keyShortcut).toMutableList()
            actionIds.removeIf { it == actionId }
            if (actionIds.isNotEmpty()) {
                for (id in actionIds) {
                    val duplicateAction = ActionManager.getInstance().getAction(id) ?: continue
                    val text = duplicateAction.getValue(Action.SHORT_DESCRIPTION) ?: continue
                    OptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(this@KeymapPanel),
                        I18n.getString("termora.settings.keymap.already-exists", toHumanText(keyStroke), text),
                        messageType = JOptionPane.ERROR_MESSAGE,
                    )
                }
                return
            }

            keymap.removeAllActionShortcuts(actionId)

            // SwitchTab 比较特殊
            if (actionId == SwitchTabAction.SWITCH_TAB) {
                for (i in KeyEvent.VK_1..KeyEvent.VK_9) {
                    keymap.addShortcut(actionId, KeyShortcut(KeyStroke.getKeyStroke(i, keyStroke.modifiers)))
                }
            } else {
                // 添加到快捷键
                keymap.addShortcut(actionId, keyShortcut)
            }

        }

        model.fireTableRowsUpdated(row, row)
        keymapManager.addKeymap(keymap)
    }

    private fun isCombinationKey(keyStroke: KeyStroke): Boolean {
        val modifiers = keyStroke.modifiers
        return (modifiers and InputEvent.CTRL_DOWN_MASK) != 0
                || (modifiers and InputEvent.SHIFT_DOWN_MASK) != 0
                || (modifiers and InputEvent.ALT_DOWN_MASK) != 0
                || (modifiers and InputEvent.META_DOWN_MASK) != 0
                || (modifiers and InputEvent.ALT_GRAPH_DOWN_MASK) != 0
    }

}