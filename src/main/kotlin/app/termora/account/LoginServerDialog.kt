package app.termora.account

import app.termora.*
import app.termora.Application.ohMyJson
import app.termora.OptionsPane.Companion.FORM_MARGIN
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.database.DatabaseManager
import com.formdev.flatlaf.FlatClientProperties
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.JXHyperlink
import org.slf4j.LoggerFactory
import java.awt.Component
import java.awt.Dimension
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.net.URI
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import kotlin.math.max

class LoginServerDialog(owner: Window) : DialogWrapper(owner) {
    companion object {
        private val log = LoggerFactory.getLogger(LoginServerDialog::class.java)
    }

    private val serverComboBox = OutlineComboBox<Server>()
    private val okAction = OkAction(I18n.getString("termora.settings.account.login"))
    private val cancelAction = super.createCancelAction()
    private val cancelButton = super.createJButtonForAction(cancelAction)
    private val singaporeServer =
        Server(I18n.getString("termora.settings.account.server-singapore"), "https://account.termora.app")
    private val chinaServer =
        Server(I18n.getString("termora.settings.account.server-china"), "https://account.termora.cn")
    var server: Server? = null

    init {
        isModal = true
        isResizable = false
        controlsVisible = false
        title = I18n.getString("termora.settings.account.login")
        init()
        pack()
        size = Dimension(max(preferredSize.width, UIManager.getInt("Dialog.width") - 250), preferredSize.height)
        setLocationRelativeTo(owner)


        addWindowListener(object : WindowAdapter() {
            override fun windowOpened(e: WindowEvent) {
                removeWindowListener(this)
            }
        })
    }

    override fun createCenterPanel(): JComponent {
        val layout = FormLayout(
            "left:pref, $FORM_MARGIN, default:grow, $FORM_MARGIN, pref",
            "pref, $FORM_MARGIN"
        )

        var rows = 1
        val step = 2

//        serverComboBox.addItem(singaporeServer)
        serverComboBox.addItem(chinaServer)

        val properties = DatabaseManager.getInstance().properties
        val servers = (runCatching {
            ohMyJson.decodeFromString<List<Server>>(properties.getString("login-servers", "[]"))
        }.getOrNull() ?: emptyList()).toMutableList()
        for (server in servers) {
            serverComboBox.addItem(Server(server.name, server.server))
        }


        serverComboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                if (value is Server) {
                    if (isSelected) {
                        return super.getListCellRendererComponent(
                            list,
                            "[${value.name}] ${value.server}",
                            index,
                            true,
                            cellHasFocus
                        )
                    }
                    val color = UIManager.getColor("textInactiveText")
                    return super.getListCellRendererComponent(
                        list,
                        "<html><font color=rgb(${color.red},${color.green},${color.blue})>[${value.name}]</font> ${value.server}</html>",
                        index,
                        false,
                        cellHasFocus
                    )
                }
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            }
        }


        val dialog = this
        val newAction = object : AnAction(I18n.getString("termora.settings.account.add-server")) {
            override fun actionPerformed(evt: AnActionEvent) {
                if (serverComboBox.itemCount < 1 || serverComboBox.selectedItem == singaporeServer || serverComboBox.selectedItem == chinaServer) {
                    val c = NewServerDialog(dialog)
                    c.isVisible = true
                    val server = c.server ?: return
                    serverComboBox.addItem(server)
                    serverComboBox.selectedItem = server
                    servers.add(server)
                    properties.putString("login-servers", ohMyJson.encodeToString(servers))
                } else {
                    if (OptionPane.showConfirmDialog(
                            dialog,
                            I18n.getString("termora.keymgr.delete-warning"),
                            I18n.getString("termora.remove"),
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE
                        ) == JOptionPane.YES_OPTION
                    ) {
                        val item = serverComboBox.selectedItem
                        serverComboBox.removeItem(item)
                        servers.removeIf { it == item }
                        properties.putString("login-servers", ohMyJson.encodeToString(servers))
                    }
                }
            }
        }

        fun refreshButton() {
            if (serverComboBox.selectedItem == singaporeServer || serverComboBox.selectedItem == chinaServer || serverComboBox.itemCount < 1) {
                newAction.name = I18n.getString("termora.settings.account.add-server")
            } else {
                newAction.name = I18n.getString("termora.remove")
            }
        }

        val newServer = JXHyperlink(newAction)
        newServer.isFocusable = false
        serverComboBox.addItemListener { refreshButton() }

        serverComboBox.model.addListDataListener(object : ListDataListener {
            override fun intervalAdded(e: ListDataEvent?) {
                refreshButton()
            }

            override fun intervalRemoved(e: ListDataEvent?) {
                refreshButton()
            }

            override fun contentsChanged(e: ListDataEvent?) {
                refreshButton()
            }

        })


        return FormBuilder.create().layout(layout).debug(false).padding("0dlu, $FORM_MARGIN, 0dlu, $FORM_MARGIN")
            .add("${I18n.getString("termora.settings.account.server")}:").xy(1, rows)
            .add(serverComboBox).xy(3, rows)
            .add(newServer).xy(5, rows).apply { rows += step }
            .build()
    }


    override fun createOkAction(): AbstractAction {
        return okAction
    }

    override fun createCancelAction(): AbstractAction {
        return cancelAction
    }

    override fun createJButtonForAction(action: Action): JButton {
        if (action == cancelAction) {
            return cancelButton
        }
        return super.createJButtonForAction(action)
    }

    private class NewServerDialog(owner: Window) : DialogWrapper(owner) {
        private val nameTextField = OutlineTextField(128)
        private val serverTextField = OutlineTextField(256)
        var server: Server? = null

        init {
            isModal = true
            isResizable = false
            controlsVisible = false
            title = I18n.getString("termora.settings.account.new-server")
            init()
            pack()
            size = Dimension(max(preferredSize.width, UIManager.getInt("Dialog.width") - 320), preferredSize.height)
            setLocationRelativeTo(owner)
        }

        override fun createCenterPanel(): JComponent {
            val layout = FormLayout(
                "left:pref, $FORM_MARGIN, default:grow, $FORM_MARGIN, pref",
                "pref, $FORM_MARGIN, pref, $FORM_MARGIN"
            )

            var rows = 1
            val step = 2

            val deploy = JXHyperlink(object : AnAction(I18n.getString("termora.settings.account.deploy-server")) {
                override fun actionPerformed(evt: AnActionEvent) {
                    Application.browse(URI.create("https://github.com/TermoraDev/termora-backend"))
                }
            })
            deploy.isFocusable = false


            return FormBuilder.create().layout(layout).debug(false).padding("0dlu, $FORM_MARGIN, 0dlu, $FORM_MARGIN")
                .add("${I18n.getString("termora.new-host.general.name")}:").xy(1, rows)
                .add(nameTextField).xyw(3, rows, 3).apply { rows += step }
                .add("${I18n.getString("termora.settings.account.server")}:").xy(1, rows)
                .add(serverTextField).xyw(3, rows, 3)
//                .add(deploy).xy(5, rows).apply { rows += step }
                .build()
        }

        override fun doOKAction() {
            if (nameTextField.text.isBlank()) {
                nameTextField.outline = FlatClientProperties.OUTLINE_ERROR
                nameTextField.requestFocusInWindow()
                return
            }
            val uri = runCatching { URI.create(serverTextField.text) }.getOrNull()
            val isHttp = uri != null && StringUtils.equalsAnyIgnoreCase(uri.scheme, "http", "https")
            if (serverTextField.text.isBlank() || isHttp.not()) {
                serverTextField.outline = FlatClientProperties.OUTLINE_ERROR
                serverTextField.requestFocusInWindow()
                return
            }

            server = Server(nameTextField.text.trim(), serverTextField.text.trim())
            super.doOKAction()
        }

        override fun doCancelAction() {
            server = null
            super.doCancelAction()
        }
    }

    override fun doOKAction() {

        server = serverComboBox.selectedItem as? Server
        if (server == null) {
            serverComboBox.outline = FlatClientProperties.OUTLINE_ERROR
            serverComboBox.requestFocusInWindow()
            return
        }


        super.doOKAction()
    }


    override fun doCancelAction() {
        server = null
        super.doCancelAction()
    }
}
