package app.termora.transfer

import app.termora.*
import app.termora.database.DatabaseManager
import app.termora.terminal.DataKey
import app.termora.transfer.TransportPanel.Companion.isLocallyFileSystem
import java.beans.PropertyChangeListener
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

internal class TransportTerminalTab : RememberFocusTerminalTab() {
    private val transportViewer = TransportViewer()
    private val sftp get() = DatabaseManager.getInstance().sftp
    private val transferManager get() = transportViewer.getTransferManager()
    private val leftTabbed get() = transportViewer.getLeftTabbed()
    val rightTabbed get() = transportViewer.getRightTabbed()

    init {
        Disposer.register(this, transportViewer)
    }

    override fun getTitle(): String {
        return I18n.getString("termora.transport.sftp")
    }

    override fun getIcon(): Icon {
        return Icons.folder
    }

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    }

    override fun canClose(): Boolean {
        return sftp.pinTab.not()
    }

    override fun willBeClose(): Boolean {
        if (canClose().not()) return false

        if (transferManager.getTransferCount() > 0) {
            return OptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(getJComponent()),
                I18n.getString("termora.transport.sftp.close-tab"),
                messageType = JOptionPane.QUESTION_MESSAGE,
                optionType = JOptionPane.OK_CANCEL_OPTION
            ) == JOptionPane.OK_OPTION
        }

        if (hasActiveTab(leftTabbed) || hasActiveTab(rightTabbed)) {
            return OptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(getJComponent()),
                I18n.getString("termora.transport.sftp.close-tab-has-active-session"),
                messageType = JOptionPane.QUESTION_MESSAGE,
                optionType = JOptionPane.OK_CANCEL_OPTION
            ) == JOptionPane.OK_OPTION
        }

        return true
    }

    private fun hasActiveTab(tabbed: TransportTabbed): Boolean {
        for (i in 0 until tabbed.tabCount) {
            val c = tabbed.getComponentAt(i) ?: continue
            if (c is TransportPanel && c.loader.isOpened()) {
                if (c.loader.getSyncTransportSupport().getFileSystem().isLocallyFileSystem().not()) {
                    return true
                }
            }
        }
        return false
    }

    override fun getJComponent(): JComponent {
        return transportViewer
    }

    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        return null
    }
}