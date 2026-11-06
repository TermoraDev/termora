package app.termora.tunneling

import app.termora.Host
import app.termora.I18n
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

internal class SSHHostPicker(owner: Window, private val hosts: List<Host>) :
    JDialog(owner, I18n.getString("termora.tunnels.host"), ModalityType.APPLICATION_MODAL) {
    private val filter = JTextField()
    private val model = DefaultListModel<Host>()
    private val list = JList(model)
    private val confirm = JButton(I18n.getString("termora.tunnels.select"))
    private var result: Host? = null

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        val content = JPanel(BorderLayout(8, 8))
        content.border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        val search = JPanel(BorderLayout(0, 6))
        search.add(JLabel(I18n.getString("termora.tunnels.filter-host")), BorderLayout.NORTH)
        search.add(filter, BorderLayout.CENTER)
        content.add(search, BorderLayout.NORTH)
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): Component {
                super.getListCellRendererComponent(list, value, index, selected, focus)
                val host = value as Host
                text = "${host.name} (${host.username}@${host.host}:${host.port})"
                return this
            }
        }
        content.add(JScrollPane(list), BorderLayout.CENTER)
        val buttons = JPanel(FlowLayout(FlowLayout.TRAILING))
        confirm.addActionListener { accept() }
        buttons.add(confirm)
        buttons.add(JButton(I18n.getString("termora.tunnels.cancel")).apply { addActionListener { dispose() } })
        content.add(buttons, BorderLayout.SOUTH)
        contentPane = content
        rootPane.defaultButton = confirm
        list.addListSelectionListener { confirm.isEnabled = list.selectedValue != null }
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && list.locationToIndex(e.point).let { it >= 0 && list.getCellBounds(it, it).contains(e.point) }) accept()
            }
        })
        filter.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = update()
            override fun removeUpdate(e: DocumentEvent) = update()
            override fun changedUpdate(e: DocumentEvent) = update()
        })
        rootPane.registerKeyboardAction({ dispose() }, KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW)
        update()
        size = Dimension(600, 420)
        setLocationRelativeTo(owner)
    }

    private fun update() {
        val selected = list.selectedValue?.id
        val query = filter.text.trim()
        model.clear()
        hosts.filter { it.name.contains(query, true) || it.host.contains(query, true) }.forEach(model::addElement)
        val index = (0 until model.size).firstOrNull { model[it].id == selected } ?: if (model.isEmpty) -1 else 0
        list.selectedIndex = index
        confirm.isEnabled = index >= 0
    }

    private fun accept() {
        result = list.selectedValue ?: return
        dispose()
    }

    fun choose(): Host? {
        SwingUtilities.invokeLater { filter.requestFocusInWindow() }
        isVisible = true
        return result
    }
}
