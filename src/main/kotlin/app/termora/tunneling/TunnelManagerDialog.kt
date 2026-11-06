package app.termora.tunneling

import app.termora.*
import app.termora.plugin.internal.ssh.SSHTunnelingOption
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel

internal class TunnelManagerDialog(owner: Window) : JDialog(owner, I18n.getString("termora.tunnels.title"), ModalityType.MODELESS) {
    private data class Row(val host: Host, val tunnel: Tunneling, val index: Int)
    private var rows = emptyList<Row>()
    private fun label(key: String) = I18n.getString("termora.tunnels.$key")
    private val model = object : DefaultTableModel(arrayOf("Host", "Name", "Type", "Source", "Destination", "Auto start", "Status"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val table = JTable(model)
    private val timer = Timer(1000) { refresh() }

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        layout = BorderLayout(8, 8)
        val keys = listOf("host", "name", "type", "source", "destination", "auto-start", "status")
        keys.forEachIndexed { index, key -> table.columnModel.getColumn(index).headerValue = label(key) }
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.autoCreateRowSorter = true
        add(JScrollPane(table), BorderLayout.CENTER)
        val buttons = JPanel(FlowLayout(FlowLayout.LEADING))
        fun button(key: String, action: () -> Unit) {
            buttons.add(JButton(label(key)).apply { addActionListener { action() } })
        }
        button("add") { edit(null) }
        button("edit") { selected()?.let { edit(it) } }
        button("delete") { selected()?.let { row ->
            if (JOptionPane.showConfirmDialog(this, label("confirm-delete"), title, JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) save(row, null)
        } }
        button("auto-start") { selected()?.let { save(it, it.tunnel.copy(autoStart = !it.tunnel.autoStart)) } }
        button("start") { selected()?.let { TunnelManager.start(it.host, it.tunnel, this) }; refresh() }
        button("stop") { selected()?.let { TunnelManager.stop(it.host, it.tunnel) }; refresh() }
        add(buttons, BorderLayout.NORTH)
        add(JLabel(label("hint")), BorderLayout.SOUTH)
        size = Dimension(1100, 500)
        setLocationRelativeTo(owner)
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) { timer.stop() }
        })
        refresh()
        timer.start()
    }

    private fun selected(): Row? = table.selectedRow.takeIf { it >= 0 }?.let { rows.getOrNull(table.convertRowIndexToModel(it)) }

    private fun refresh() {
        val selection = selected()
        val updated = HostManager.getInstance().hosts().filter { it.protocol == "SSH" }.flatMap { host ->
            host.tunnelings.mapIndexed { index, tunnel -> Row(host, tunnel, index) }
        }
        if (updated == rows && model.rowCount == updated.size) {
            rows.forEachIndexed { index, row -> model.setValueAt(TunnelManager.status(row.host, row.tunnel), index, 6) }
            return
        }
        rows = updated
        model.rowCount = 0
        for (row in rows) {
            val t = row.tunnel
            model.addRow(arrayOf<Any>(row.host.name, t.name, t.type, "${t.sourceHost}:${t.sourcePort}",
                if (t.type == TunnelingType.Dynamic) "SOCKS" else "${t.destinationHost}:${t.destinationPort}",
                t.autoStart, TunnelManager.status(row.host, t)))
        }
        val index = rows.indexOfFirst { it.host.id == selection?.host?.id && it.index == selection.index }
        if (index >= 0) table.setRowSelectionInterval(table.convertRowIndexToView(index), table.convertRowIndexToView(index))
    }

    private fun edit(row: Row?) {
        val hosts = HostManager.getInstance().hosts().filter { it.protocol == "SSH" && !it.isFolder }
        val host = row?.host ?: run {
            if (hosts.isEmpty()) { JOptionPane.showMessageDialog(this, label("no-hosts")); return }
            SSHHostPicker(this, hosts).choose() ?: return
        }
        val dialog = SSHTunnelingOption.PortForwardingDialog(this, row?.tunnel)
        dialog.isVisible = true
        val tunnel = dialog.tunneling ?: return
        if (row != null) save(row, tunnel) else {
            val current = HostManager.getInstance().getHost(host.id) ?: return
            HostManager.getInstance().addHost(current.copy(tunnelings = current.tunnelings + tunnel))
            refresh()
        }
    }

    private fun save(row: Row, replacement: Tunneling?) {
        val current = HostManager.getInstance().getHost(row.host.id) ?: return
        if (current.tunnelings.getOrNull(row.index) != row.tunnel) { refresh(); return }
        TunnelManager.stop(row.host, row.tunnel)
        val tunnels = current.tunnelings.toMutableList()
        if (replacement == null) tunnels.removeAt(row.index) else tunnels[row.index] = replacement
        HostManager.getInstance().addHost(current.copy(tunnelings = tunnels))
        refresh()
    }
}
