package app.termora.actions

import app.termora.*
import app.termora.Application.ohMyJson
import app.termora.OptionsPane.Companion.FORM_MARGIN
import app.termora.database.DatabaseManager
import app.termora.protocol.ProtocolProvider
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import kotlinx.serialization.Serializable
import org.apache.commons.lang3.exception.ExceptionUtils
import java.awt.Dimension
import java.awt.Window
import java.net.URI
import java.util.*
import javax.swing.*

class QuickConnectAction private constructor() : AnAction(I18n.getString("termora.actions.quick-connect"), Icons.find) {
    companion object {
        const val QUICK_CONNECT = "QuickConnectAction"
        val instance = QuickConnectAction()
    }


    init {
        putValue(SHORT_DESCRIPTION, I18n.getString("termora.actions.quick-connect"))
    }

    override fun actionPerformed(evt: AnActionEvent) {
        val scope = evt.getData(DataProviders.WindowScope) ?: return
        val dialog = QuickConnectDialog(scope.window)
        dialog.isVisible = true
    }

    private class QuickConnectDialog(owner: Window) : DialogWrapper(owner) {
        private val properties get() = DatabaseManager.getInstance().properties
        private val hostComboBox = OutlineComboBox<String>()
        private val usernameTextField = OutlineTextField(256)
        private val passwordTextField = OutlinePasswordField(256)

        init {
            isModal = true
            title = I18n.getString("termora.actions.quick-connect")
            isResizable = false
            init()
            pack()
            size = Dimension(UIManager.getInt("Dialog.width") - 250, preferredSize.height)
            setLocationRelativeTo(owner)
        }

        override fun createCenterPanel(): JComponent {
            hostComboBox.isEditable = true
            hostComboBox.placeholderText = "ssh://127.0.0.1:22"

            val histories = getHistories()
            for (history in histories) {
                if (histories.first() == history) {
                    usernameTextField.text = history.host.username
                    passwordTextField.text = history.host.authentication.password
                }
                hostComboBox.addItem(history.url)
            }

            usernameTextField.placeholderText = I18n.getString("termora.new-host.general.username")
            passwordTextField.placeholderText = I18n.getString("termora.new-host.general.password")

            val layout = FormLayout(
                "left:pref, $FORM_MARGIN, default:grow",
                "pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref"
            )

            return FormBuilder.create().layout(layout)
                .border(BorderFactory.createEmptyBorder(0, 8, 8, 8))
                .add("${I18n.getString("termora.new-host.general.protocol")}:").xy(1, 1)
                .add(hostComboBox).xy(3, 1)

                .add("${I18n.getString("termora.new-host.general.username")}:").xy(1, 3)
                .add(usernameTextField).xy(3, 3)

                .add("${I18n.getString("termora.new-host.general.password")}:").xy(1, 5)
                .add(passwordTextField).xy(3, 5)
                .build()

        }

        override fun doOKAction() {
            val host = hostComboBox.selectedItem as? String
            if (host.isNullOrBlank()) {
                hostComboBox.requestFocusInWindow()
                return
            }

            val historyHost: HistoryHost
            try {
                historyHost = getHistoryHost(host.trim())
            } catch (e: Exception) {
                hostComboBox.requestFocusInWindow()
                OptionPane.showMessageDialog(
                    this,
                    e.message ?: ExceptionUtils.getRootCauseMessage(e),
                    messageType = JOptionPane.ERROR_MESSAGE
                )
                return
            }

            val action = ActionManager.getInstance().getAction(OpenHostAction.OPEN_HOST)
            if (action is OpenHostAction) {
                SwingUtilities.invokeLater {
                    action.actionPerformed(OpenHostActionEvent(this, historyHost.host, EventObject(this)))
                }
            }
            super.doOKAction()


        }

        override fun createOkAction(): AbstractAction {
            return OkAction(I18n.getString("termora.welcome.contextmenu.connect"))
        }

        private fun getHistoryHost(host: String): HistoryHost {


            val uri = URI.create(host)
            val protocolProvider = ProtocolProvider.valueOf(uri.scheme)
            if (protocolProvider == null) {
                throw UnsupportedOperationException(I18n.getString("termora.protocol.not-supported", uri.scheme))
            }

            val historyHost = HistoryHost(
                host, Host(
                    name = uri.host,
                    protocol = uri.scheme,
                    host = uri.host,
                    port = uri.port,
                    username = usernameTextField.text.trim(),
                    authentication = Authentication.No.copy(
                        type = AuthenticationType.Password,
                        password = String(passwordTextField.password)
                    ),
                    options = Options.Default.copy(
                        extras = mutableMapOf("Temporary" to "true")
                    )
                )
            )
            val histories = getHistories().toMutableList()
            histories.removeIf { it.url == host }
            histories.addFirst(historyHost)

            if (histories.size > 20) {
                histories.removeLast()
            }

            properties.putString("QuickConnect.historyHosts", ohMyJson.encodeToString(histories))

            return historyHost
        }

        private fun getHistories(): List<HistoryHost> {
            val text = properties.getString("QuickConnect.historyHosts", "[]")
            return ohMyJson.runCatching { ohMyJson.decodeFromString<List<HistoryHost>>(text) }
                .getOrNull() ?: emptyList()
        }

        override fun addNotify() {
            super.addNotify()
            controlsVisible = false
        }

    }

    @Serializable
    private data class HistoryHost(
        val url: String,
        val host: Host,
    )

}