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

internal class SSHJumpHostsOption : JPanel(BorderLayout()), OptionsPane.Option {
    private val owner: Window get() = SwingUtilities.getWindowAncestor(this)

    val jumpHosts = mutableListOf<Host>()
    var filter: (host: Host) -> Boolean = { true }

    private val model = object : DefaultTableModel() {

        override fun isCellEditable(row: Int, column: Int): Boolean {
            return false
        }

        override fun getRowCount(): Int {
            return jumpHosts.size
        }


        override fun getValueAt(row: Int, column: Int): Any {
            val host = jumpHosts.getOrNull(row) ?: return StringUtils.EMPTY
            return if (column == 0)
                host.name
            else "${host.host}:${host.port}"
        }
    }
    private val table = JTable(model)
    private val addBtn = JButton(I18n.getString("termora.new-host.tunneling.add"))
    private val moveUpBtn = JButton(I18n.getString("termora.transport.bookmarks.up"))
    private val moveDownBtn = JButton(I18n.getString("termora.transport.bookmarks.down"))
    private val deleteBtn = JButton(I18n.getString("termora.new-host.tunneling.delete"))

    init {
        initView()
        initEvents()
    }

    private fun initView() {
        val scrollPane = JScrollPane(table)

        model.addColumn(I18n.getString("termora.new-host.general.name"))
        model.addColumn(I18n.getString("termora.new-host.general.host"))

        table.putClientProperty(
            FlatClientProperties.STYLE, mapOf(
                "showHorizontalLines" to true,
                "showVerticalLines" to true,
            )
        )
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

        moveUpBtn.isFocusable = false
        moveDownBtn.isFocusable = false
        deleteBtn.isFocusable = false
        moveUpBtn.isEnabled = false
        moveDownBtn.isEnabled = false
        deleteBtn.isEnabled = false
        addBtn.isFocusable = false

        val box = Box.createHorizontalBox()
        box.add(addBtn)
        box.add(Box.createHorizontalStrut(4))
        box.add(deleteBtn)
        box.add(Box.createHorizontalStrut(4))
        box.add(moveUpBtn)
        box.add(Box.createHorizontalStrut(4))
        box.add(moveDownBtn)

        add(JLabel("${getTitle()}:"), BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        add(box, BorderLayout.SOUTH)
    }

    private fun initEvents() {
        addBtn.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                val dialog = NewHostTreeDialog(owner, object : Filter {
                    override fun filter(node: Any): Boolean {
                        return node is HostTreeNode && jumpHosts.none { it.id == node.host.id } && filter.invoke(
                            node.host
                        )
                    }
                })
                dialog.setTreeName("HostOptionsPane.JumpHostsOption.Tree")
                dialog.setLocationRelativeTo(owner)
                dialog.isVisible = true
                val hosts = dialog.hosts
                if (hosts.isEmpty()) {
                    return
                }

                hosts.forEach {
                    val rowCount = model.rowCount
                    jumpHosts.add(it)
                    model.fireTableRowsInserted(rowCount, rowCount + 1)
                }
            }
        })

        deleteBtn.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val rows = table.selectedRows.sortedDescending()
                if (rows.isEmpty()) return
                for (row in rows) {
                    jumpHosts.removeAt(row)
                    model.fireTableRowsDeleted(row, row)
                }
            }
        })

        table.selectionModel.addListSelectionListener {
            deleteBtn.isEnabled = table.selectedRowCount > 0
            moveUpBtn.isEnabled = deleteBtn.isEnabled && !table.selectedRows.contains(0)
            moveDownBtn.isEnabled = deleteBtn.isEnabled && !table.selectedRows.contains(table.rowCount - 1)
        }


        moveUpBtn.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val rows = table.selectedRows.sorted()
                if (rows.isEmpty()) return

                table.clearSelection()

                for (row in rows) {
                    val host = jumpHosts[(row)]
                    jumpHosts.removeAt(row)
                    jumpHosts.add(row - 1, host)
                    table.addRowSelectionInterval(row - 1, row - 1)
                }
            }
        })

        moveDownBtn.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val rows = table.selectedRows.sortedDescending()
                if (rows.isEmpty()) return

                table.clearSelection()

                for (row in rows) {
                    val host = jumpHosts[(row)]
                    jumpHosts.removeAt(row)
                    jumpHosts.add(row + 1, host)
                    table.addRowSelectionInterval(row + 1, row + 1)
                }
            }
        })
    }

    override fun getIcon(isSelected: Boolean): Icon {
        return Icons.server
    }

    override fun getTitle(): String {
        return I18n.getString("termora.new-host.jump-hosts")
    }

    override fun getJComponent(): JComponent {
        return this
    }


}
