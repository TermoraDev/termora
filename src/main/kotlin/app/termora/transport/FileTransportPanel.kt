package app.termora.transport

import app.termora.Disposable
import app.termora.I18n
import app.termora.OptionPane
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer

class FileTransportPanel(
    private val transportManager: TransportManager
) : JPanel(BorderLayout()), Disposable {

    private val tableModel = FileTransportTableModel(transportManager)
    private val table = JTable(tableModel)

    init {
        initView()
        initEvents()
    }

    private fun initView() {
        table.fillsViewportHeight = true
        table.autoResizeMode = JTable.AUTO_RESIZE_OFF
        table.putClientProperty(
            FlatClientProperties.STYLE, mapOf(
                "showHorizontalLines" to true,
                "showVerticalLines" to true,
                "cellMargins" to Insets(2, 2, 2, 2)
            )
        )
        table.columnModel.getColumn(FileTransportTableModel.COLUMN_NAME).preferredWidth = 200
        table.columnModel.getColumn(FileTransportTableModel.COLUMN_SOURCE_PATH).preferredWidth = 200
        table.columnModel.getColumn(FileTransportTableModel.COLUMN_TARGET_PATH).preferredWidth = 200

        table.columnModel.getColumn(FileTransportTableModel.COLUMN_STATUS).preferredWidth = 100
        table.columnModel.getColumn(FileTransportTableModel.COLUMN_PROGRESS).preferredWidth = 150
        table.columnModel.getColumn(FileTransportTableModel.COLUMN_SIZE).preferredWidth = 140
        table.columnModel.getColumn(FileTransportTableModel.COLUMN_SPEED).preferredWidth = 80

        val centerTableCellRenderer = DefaultTableCellRenderer().apply { horizontalAlignment = SwingConstants.CENTER }
        table.columnModel.getColumn(FileTransportTableModel.COLUMN_STATUS).cellRenderer = centerTableCellRenderer
        table.columnModel.getColumn(FileTransportTableModel.COLUMN_SIZE).cellRenderer = centerTableCellRenderer
        table.columnModel.getColumn(FileTransportTableModel.COLUMN_SPEED).cellRenderer = centerTableCellRenderer
        table.columnModel.getColumn(FileTransportTableModel.COLUMN_ESTIMATED_TIME).cellRenderer =
            centerTableCellRenderer


        table.columnModel.getColumn(FileTransportTableModel.COLUMN_PROGRESS).cellRenderer =
            object : DefaultTableCellRenderer() {
                init {
                    horizontalAlignment = SwingConstants.CENTER
                }

                private var lastRow = -1

                override fun getTableCellRendererComponent(
                    table: JTable?,
                    value: Any?,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    column: Int
                ): Component {
                    lastRow = row
                    return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                }

                override fun paintComponent(g: Graphics) {
                    if (lastRow != -1) {
                        val row = tableModel.getTransport(lastRow)
                        if (row.state == TransportState.Transporting) {
                            g.color = UIManager.getColor("textHighlight")
                            g.fillRect(0, 0, (width * row.progress).toInt(), height)
                        }
                    }
                    super.paintComponent(g)
                }
            }


        add(JScrollPane(table).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)
    }


    private fun initEvents() {

        // contextmenu
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val r = table.rowAtPoint(e.point)
                    if (r >= 0 && r < table.rowCount) {
                        if (!table.isRowSelected(r)) {
                            table.setRowSelectionInterval(r, r)
                        }
                    } else {
                        table.clearSelection()
                    }

                    val rows = table.selectedRows

                    if (!table.hasFocus()) {
                        table.requestFocusInWindow()
                    }


                    showContextMenu(kotlin.runCatching {
                        rows.map { tableModel.getTransport(it) }
                    }.getOrElse { emptyList() }, e)
                }
            }
        })
    }


    private fun showContextMenu(transports: List<Transport>, event: MouseEvent) {
        val popupMenu = FlatPopupMenu()

        val delete = popupMenu.add(I18n.getString("termora.transport.jobs.contextmenu.delete")).apply {
            addActionListener {
                if (OptionPane.showConfirmDialog(
                        SwingUtilities.getWindowAncestor(this),
                        I18n.getString("termora.keymgr.delete-warning"),
                        messageType = JOptionPane.WARNING_MESSAGE
                    ) == JOptionPane.YES_OPTION
                ) {
                    for (transport in transports) {
                        transportManager.removeTransport(transport)
                    }
                }
            }
        }

        val deleteAll = popupMenu.add(I18n.getString("termora.transport.jobs.contextmenu.delete-all"))
        deleteAll.addActionListener {
            if (OptionPane.showConfirmDialog(
                    SwingUtilities.getWindowAncestor(this),
                    I18n.getString("termora.keymgr.delete-warning"),
                    messageType = JOptionPane.WARNING_MESSAGE
                ) == JOptionPane.YES_OPTION
            ) {
                transportManager.removeAllTransports()
            }
        }

        if (transports.isEmpty()) {
            delete.isEnabled = false
            deleteAll.isEnabled = transportManager.getTransports().isNotEmpty()
        }

        popupMenu.show(table, event.x, event.y)
    }

}