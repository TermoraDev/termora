package app.termora

import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Window
import java.awt.event.ActionEvent
import javax.swing.*

class HostDialog(owner: Window, host: Host? = null) : DialogWrapper(owner) {
    private val pane = if (host != null) EditHostOptionsPane(host) else HostOptionsPane()
    var host: Host? = host
        private set

    init {
        size = Dimension(UIManager.getInt("Dialog.width"), UIManager.getInt("Dialog.height"))
        isModal = true
        title = I18n.getString("termora.new-host.title")
        setLocationRelativeTo(null)

        init()
    }

    override fun createCenterPanel(): JComponent {
        pane.background = UIManager.getColor("window")

        val panel = JPanel(BorderLayout())
        panel.add(pane, BorderLayout.CENTER)
        panel.background = UIManager.getColor("window")
        panel.border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor)

        return panel
    }

    override fun createActions(): List<AbstractAction> {
        return listOf(createOkAction(), createTestConnectionAction(), CancelAction())
    }

    private fun createTestConnectionAction(): AbstractAction {
        return object : AnAction(I18n.getString("termora.new-host.test-connection")) {
            override fun actionPerformed(e: ActionEvent) {
                if (!pane.validateFields()) {
                    return
                }

                putValue(NAME, "${I18n.getString("termora.new-host.test-connection")}...")
                SwingUtilities.invokeLater {
                    testConnection(pane.getHost())
                    putValue(NAME, I18n.getString("termora.new-host.test-connection"))
                }

            }
        }
    }


    private fun testConnection(host: Host) {
        if (host.protocol != Protocol.SSH) {
            OptionPane.showMessageDialog(this, I18n.getString("termora.new-host.test-connection-successful"))
            return
        }

        var client: SshClient? = null
        var session: ClientSession? = null
        try {
            client = SshClients.openClient(host)
            session = SshClients.openSession(host, client)
            OptionPane.showMessageDialog(this, I18n.getString("termora.new-host.test-connection-successful"))
        } catch (e: Exception) {
            OptionPane.showMessageDialog(
                this, ExceptionUtils.getRootCauseMessage(e),
                messageType = JOptionPane.ERROR_MESSAGE
            )
        } finally {
            session?.close()
            client?.close()
        }

    }

    override fun doOKAction() {
        if (!pane.validateFields()) {
            return
        }
        host = pane.getHost()
        super.doOKAction()
    }


}