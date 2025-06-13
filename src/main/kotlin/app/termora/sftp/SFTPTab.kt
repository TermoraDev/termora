package app.termora.sftp

import app.termora.*
import app.termora.database.DatabaseManager
import app.termora.terminal.DataKey
import java.beans.PropertyChangeListener
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

class SFTPTab : RememberFocusTerminalTab() {
    private val sftpPanel = SFTPPanel()
    private val sftp get() = DatabaseManager.getInstance().sftp

    init {
        Disposer.register(this, sftpPanel)
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
        return !sftp.pinTab
    }

    override fun willBeClose(): Boolean {
        if (!canClose()) return false

        val transportManager = sftpPanel.getData(SFTPDataProviders.TransportManager) ?: return true
        if (transportManager.getTransportCount() > 0) {
            return OptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(getJComponent()),
                I18n.getString("termora.transport.sftp.close-tab"),
                messageType = JOptionPane.QUESTION_MESSAGE,
                optionType = JOptionPane.OK_CANCEL_OPTION
            ) == JOptionPane.OK_OPTION
        }

        val leftTabbed = sftpPanel.getData(SFTPDataProviders.LeftSFTPTabbed) ?: return true
        val rightTabbed = sftpPanel.getData(SFTPDataProviders.RightSFTPTabbed) ?: return true
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

    private fun hasActiveTab(tabbed: SFTPTabbed): Boolean {
        for (i in 0 until tabbed.tabCount) {
            val c = tabbed.getFileSystemViewPanel(i) ?: continue
            if (c.host.id != "local") {
                return true
            }
        }
        return false
    }

    override fun getJComponent(): JComponent {
        return sftpPanel
    }

    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        return sftpPanel.getData(dataKey)
    }
}