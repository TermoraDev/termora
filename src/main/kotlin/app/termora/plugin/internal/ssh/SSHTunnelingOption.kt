package app.termora.plugin.internal.ssh

import app.termora.*
import app.termora.account.AccountOwner
import app.termora.highlight.KeywordHighlight
import app.termora.keymgr.KeyManager
import app.termora.keymgr.KeyManagerDialog
import app.termora.plugin.internal.AltKeyModifier
import app.termora.plugin.internal.BasicProxyOption
import app.termora.plugin.internal.BasicTerminalOption
import app.termora.plugin.internal.telnet.TelnetHostOptionsPane.Backspace
import app.termora.tree.Filter
import app.termora.tree.HostTreeNode
import app.termora.tree.NewHostTreeDialog
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatComboBox
import com.formdev.flatlaf.ui.FlatTextBorder
import com.formdev.flatlaf.util.SystemInfo
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.lang3.StringUtils
import org.eclipse.jgit.internal.transport.sshd.agent.connector.PageantConnector
import org.eclipse.jgit.internal.transport.sshd.agent.connector.UnixDomainSocketConnector
import org.eclipse.jgit.internal.transport.sshd.agent.connector.WinPipeConnector
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

internal class SSHTunnelingOption : JPanel(BorderLayout()), OptionsPane.Option {
    private val owner: Window get() = SwingUtilities.getWindowAncestor(this)

    val tunnelings = mutableListOf<Tunneling>()
    val x11ForwardingCheckBox = JCheckBox("X DISPLAY:")
    val forwardAgentCheckBox = JCheckBox("Enable ForwardAgent")
    val x11ServerTextField = OutlineTextField(255)

    private val model = object : DefaultTableModel() {
        override fun getRowCount(): Int {
            return tunnelings.size
        }

        override fun isCellEditable(row: Int, column: Int): Boolean {
            return false
        }

        fun addRow(tunneling: Tunneling) {
            val rowCount = super.getRowCount()
            tunnelings.add(tunneling)
            super.fireTableRowsInserted(rowCount, rowCount + 1)
        }

        override fun getValueAt(row: Int, column: Int): Any {
            val tunneling = tunnelings[row]
            return when (column) {
                0 -> tunneling.name
                1 -> tunneling.type
                2 -> "${tunneling.sourceHost}:${tunneling.sourcePort}"
                3 -> "${tunneling.destinationHost}:${tunneling.destinationPort}"
                else -> super.getValueAt(row, column)
            }
        }
    }
    private val table = JTable(model)
    private val addBtn = JButton(I18n.getString("termora.new-host.tunneling.add"))
    private val editBtn = JButton(I18n.getString("termora.new-host.tunneling.edit"))
    private val deleteBtn = JButton(I18n.getString("termora.new-host.tunneling.delete"))

    init {
        initView()
        initEvents()
    }

    private fun initView() {
        val scrollPane = JScrollPane(table)

        model.addColumn(I18n.getString("termora.new-host.tunneling.table.name"))
        model.addColumn(I18n.getString("termora.new-host.tunneling.table.type"))
        model.addColumn(I18n.getString("termora.new-host.tunneling.table.source"))
        model.addColumn(I18n.getString("termora.new-host.tunneling.table.destination"))


        table.putClientProperty(
            FlatClientProperties.STYLE, mapOf(
                "showHorizontalLines" to true,
                "showVerticalLines" to true,
            )
        )
        table.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        table.border = BorderFactory.createEmptyBorder()
        table.fillsViewportHeight = true
        scrollPane.border = BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(4, 0, 4, 0),
            BorderFactory.createMatteBorder(1, 1, 1, 1, DynamicColor.Companion.BorderColor)
        )

        deleteBtn.isFocusable = false
        addBtn.isFocusable = false
        editBtn.isFocusable = false

        editBtn.isEnabled = false
        deleteBtn.isEnabled = false

        val box = Box.createHorizontalBox()
        box.add(addBtn)
        box.add(Box.createHorizontalStrut(4))
        box.add(editBtn)
        box.add(Box.createHorizontalStrut(4))
        box.add(deleteBtn)

        x11ForwardingCheckBox.isFocusable = false
        forwardAgentCheckBox.isFocusable = false

        if (x11ServerTextField.text.isBlank()) {
            x11ServerTextField.text = "localhost:0"
        }

        val x11Forwarding = Box.createHorizontalBox()
        x11Forwarding.border = BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("X11 Forwarding"),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)
        )
        x11Forwarding.add(x11ForwardingCheckBox)
        x11Forwarding.add(x11ServerTextField)

        val forwardAgent = Box.createHorizontalBox()
        forwardAgent.border = BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Agent Forwarding"),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)
        )
        forwardAgent.add(forwardAgentCheckBox)

        x11ServerTextField.isEnabled = x11ForwardingCheckBox.isSelected

        val panel = JPanel(BorderLayout())
        panel.add(JLabel("TCP/IP Forwarding:"), BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)
        panel.add(box, BorderLayout.SOUTH)
        panel.border = BorderFactory.createEmptyBorder(0, 0, 8, 0)

        val forwardingBox = Box.createHorizontalBox()
        forwardingBox.add(x11Forwarding)
        forwardingBox.add(Box.createHorizontalStrut(4))
        forwardingBox.add(forwardAgent)

        add(panel, BorderLayout.CENTER)
        add(forwardingBox, BorderLayout.SOUTH)

    }

    private fun initEvents() {
        x11ForwardingCheckBox.addChangeListener { x11ServerTextField.isEnabled = x11ForwardingCheckBox.isSelected }

        addBtn.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                val dialog = PortForwardingDialog(owner)
                dialog.isVisible = true
                val tunneling = dialog.tunneling ?: return
                model.addRow(tunneling)
            }
        })


        editBtn.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                val row = table.selectedRow
                if (row < 0) {
                    return
                }
                val dialog = PortForwardingDialog(
                    owner,
                    tunnelings[row]
                )
                dialog.isVisible = true
                tunnelings[row] = dialog.tunneling ?: return
                model.fireTableRowsUpdated(row, row)
            }
        })

        deleteBtn.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val rows = table.selectedRows
                if (rows.isEmpty()) return
                rows.sortDescending()
                for (row in rows) {
                    tunnelings.removeAt(row)
                    model.fireTableRowsDeleted(row, row)
                }
            }
        })

        table.selectionModel.addListSelectionListener {
            editBtn.isEnabled = table.selectedRowCount > 0
            deleteBtn.isEnabled = editBtn.isEnabled
        }

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount % 2 == 0 && SwingUtilities.isLeftMouseButton(e)) {
                    editBtn.actionListeners.forEach {
                        it.actionPerformed(
                            ActionEvent(
                                editBtn,
                                ActionEvent.ACTION_PERFORMED,
                                StringUtils.EMPTY
                            )
                        )
                    }
                }
            }
        })
    }

    override fun getIcon(isSelected: Boolean): Icon {
        return Icons.showWriteAccess
    }

    override fun getTitle(): String {
        return I18n.getString("termora.new-host.tunneling")
    }

    override fun getJComponent(): JComponent {
        return this
    }

    private inner class PortForwardingDialog(
        owner: Window,
        var tunneling: Tunneling? = null
    ) : DialogWrapper(owner) {
        private val formMargin = "4dlu"
        private val typeComboBox = FlatComboBox<TunnelingType>()
        private val nameTextField = OutlineTextField(32)
        private val localHostTextField = OutlineTextField()
        private val localPortSpinner = PortSpinner()
        private val remoteHostTextField = OutlineTextField()
        private val remotePortSpinner = PortSpinner()

        init {
            isModal = true
            title = I18n.getString("termora.new-host.tunneling")
            controlsVisible = false

            typeComboBox.addItem(TunnelingType.Local)
            typeComboBox.addItem(TunnelingType.Remote)
            typeComboBox.addItem(TunnelingType.Dynamic)

            localHostTextField.text = "127.0.0.1"
            localPortSpinner.value = 1080

            remoteHostTextField.text = "127.0.0.1"

            typeComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    remoteHostTextField.isEnabled = typeComboBox.selectedItem != TunnelingType.Dynamic
                    remotePortSpinner.isEnabled = remoteHostTextField.isEnabled
                }
            }

            tunneling?.let {
                localHostTextField.text = it.sourceHost
                localPortSpinner.value = it.sourcePort
                remoteHostTextField.text = it.destinationHost
                remotePortSpinner.value = it.destinationPort
                nameTextField.text = it.name
                typeComboBox.selectedItem = it.type
            }

            init()
            pack()
            size = Dimension(UIManager.getInt("Dialog.width") - 300, size.height)
            setLocationRelativeTo(owner)

        }

        override fun doOKAction() {
            if (nameTextField.text.isBlank()) {
                nameTextField.outline = "error"
                nameTextField.requestFocusInWindow()
                return
            } else if (localHostTextField.text.isBlank()) {
                localHostTextField.outline = "error"
                localHostTextField.requestFocusInWindow()
                return
            } else if (remoteHostTextField.text.isBlank()) {
                remoteHostTextField.outline = "error"
                remoteHostTextField.requestFocusInWindow()
                return
            }

            tunneling = Tunneling(
                name = nameTextField.text,
                type = typeComboBox.selectedItem as TunnelingType,
                sourceHost = localHostTextField.text,
                sourcePort = localPortSpinner.value as Int,
                destinationHost = remoteHostTextField.text,
                destinationPort = remotePortSpinner.value as Int,
            )

            super.doOKAction()
        }

        override fun doCancelAction() {
            tunneling = null
            super.doCancelAction()
        }

        override fun createCenterPanel(): JComponent {
            val layout = FormLayout(
                "left:pref, $formMargin, default:grow, $formMargin, pref",
                "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
            )

            var rows = 1
            val step = 2
            return FormBuilder.create().layout(layout).padding("0dlu, $formMargin, $formMargin, $formMargin")
                .add("${I18n.getString("termora.new-host.tunneling.table.name")}:").xy(1, rows)
                .add(nameTextField).xyw(3, rows, 3).apply { rows += step }
                .add("${I18n.getString("termora.new-host.tunneling.table.type")}:").xy(1, rows)
                .add(typeComboBox).xyw(3, rows, 3).apply { rows += step }
                .add("${I18n.getString("termora.new-host.tunneling.table.source")}:").xy(1, rows)
                .add(localHostTextField).xy(3, rows)
                .add(localPortSpinner).xy(5, rows).apply { rows += step }
                .add("${I18n.getString("termora.new-host.tunneling.table.destination")}:").xy(1, rows)
                .add(remoteHostTextField).xy(3, rows)
                .add(remotePortSpinner).xy(5, rows).apply { rows += step }
                .build()
        }


    }
}


