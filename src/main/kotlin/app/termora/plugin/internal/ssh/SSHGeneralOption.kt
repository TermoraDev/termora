package app.termora.plugin.internal.ssh

import app.termora.*
import app.termora.OptionsPane.Companion.FORM_MARGIN
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

internal class SSHGeneralOption(private val accountOwner: AccountOwner) : JPanel(BorderLayout()), OptionsPane.Option {
    private val owner: Window get() = SwingUtilities.getWindowAncestor(this)
    var setHostMode = false

    val portTextField = PortSpinner()
    val nameTextField = OutlineTextField(128)
    val usernameTextField = OutlineTextField(128)
    val hostTextField = OutlineTextField(255)
    private val passwordPanel = JPanel(BorderLayout())
    private val chooseKeyBtn = JButton(Icons.greyKey)
    val passwordTextField = OutlinePasswordField(255)
    val sshAgentComboBox = OutlineComboBox<String>()
    val publicKeyComboBox = OutlineComboBox<String>()
    val remarkTextArea = FixedLengthTextArea(512)
    val autoReconnect = JCheckBox(I18n.getString("termora.new-host.auto-reconnect"))
    val authenticationTypeComboBox = FlatComboBox<AuthenticationType>()

    private var hostFocused = false

    init {
        initView()
        initEvents()
    }

    private fun initView() {
        add(getCenterComponent(), BorderLayout.CENTER)

        publicKeyComboBox.isEditable = false
        chooseKeyBtn.isFocusable = false

        // 只有 Windows 允许修改
        sshAgentComboBox.isEditable = SystemInfo.isWindows
        sshAgentComboBox.isEnabled = SystemInfo.isWindows


        publicKeyComboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                var text = StringUtils.EMPTY
                if (value is String) {
                    text = KeyManager.getInstance().getOhKeyPair(value)?.name ?: text
                }
                return super.getListCellRendererComponent(
                    list,
                    text,
                    index,
                    isSelected,
                    cellHasFocus
                )
            }
        }

        authenticationTypeComboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                var text = value?.toString() ?: ""
                when (value) {
                    AuthenticationType.Password -> {
                        text = "Password"
                    }

                    AuthenticationType.PublicKey -> {
                        text = "Public Key"
                    }

                    AuthenticationType.KeyboardInteractive -> {
                        text = "Keyboard Interactive"
                    }
                }
                return super.getListCellRendererComponent(
                    list,
                    text,
                    index,
                    isSelected,
                    cellHasFocus
                )
            }
        }

        authenticationTypeComboBox.addItem(AuthenticationType.No)
        authenticationTypeComboBox.addItem(AuthenticationType.Password)
        authenticationTypeComboBox.addItem(AuthenticationType.PublicKey)
        authenticationTypeComboBox.addItem(AuthenticationType.SSHAgent)

        if (SystemInfo.isWindows) {
            // 不要修改 addItem 的顺序，因为第一个是默认的
            sshAgentComboBox.addItem(PageantConnector.DESCRIPTOR.identityAgent)
            sshAgentComboBox.addItem(WinPipeConnector.DESCRIPTOR.identityAgent)
            sshAgentComboBox.placeholderText = PageantConnector.DESCRIPTOR.identityAgent
        } else {
            sshAgentComboBox.addItem(UnixDomainSocketConnector.DESCRIPTOR.identityAgent)
            sshAgentComboBox.placeholderText = UnixDomainSocketConnector.DESCRIPTOR.identityAgent
        }

        authenticationTypeComboBox.selectedItem = AuthenticationType.Password

    }

    private fun initEvents() {

        authenticationTypeComboBox.addItemListener {
            if (it.stateChange == ItemEvent.SELECTED) {
                switchPasswordComponent()
            }
        }

        chooseKeyBtn.addActionListener {
            chooseKeyPair()
        }

        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                SwingUtilities.invokeLater { nameTextField.requestFocusInWindow() }
                removeComponentListener(this)
            }
        })

        hostTextField.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) {
                hostTextField.removeFocusListener(this)
                hostFocused = true
            }
        })

        nameTextField.document.addDocumentListener(object : DocumentAdaptor() {
            override fun changedUpdate(e: DocumentEvent) {
                if (nameTextField.hasFocus().not()) return

                if (hostFocused || setHostMode) {
                    nameTextField.document.removeDocumentListener(this)
                    return
                }

                hostTextField.text = nameTextField.text
            }
        })
    }

    private fun chooseKeyPair() {
        val dialog = KeyManagerDialog(
            owner,
            selectMode = true,
            accountOwner = accountOwner,
        )
        dialog.pack()
        dialog.setLocationRelativeTo(owner)
        dialog.isVisible = true

        val selectedItem = publicKeyComboBox.selectedItem

        publicKeyComboBox.removeAllItems()
        for (keyPair in KeyManager.getInstance().getOhKeyPairs(accountOwner.id)) {
            publicKeyComboBox.addItem(keyPair.id)
        }
        publicKeyComboBox.selectedItem = selectedItem

        if (!dialog.ok) {
            return
        }

        publicKeyComboBox.selectedItem = dialog.getLasOhKeyPair()?.id ?: return
    }

    override fun getIcon(isSelected: Boolean): Icon {
        return Icons.settings
    }

    override fun getTitle(): String {
        return I18n.getString("termora.new-host.general")
    }

    override fun getJComponent(): JComponent {
        return this
    }

    private fun getCenterComponent(): JComponent {
        val layout = FormLayout(
            "left:pref, $FORM_MARGIN, default:grow, $FORM_MARGIN, pref, $FORM_MARGIN, default",
            "pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref"
        )
        remarkTextArea.setFocusTraversalKeys(
            KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .getDefaultFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)
        )
        remarkTextArea.setFocusTraversalKeys(
            KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .getDefaultFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS)
        )

        remarkTextArea.rows = 8
        remarkTextArea.lineWrap = true
        remarkTextArea.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)

        switchPasswordComponent()

        var rows = 1
        val step = 2
        val panel = FormBuilder.create().layout(layout)
            .add("${I18n.getString("termora.new-host.general.name")}:").xy(1, rows)
            .add(nameTextField).xyw(3, rows, 5).apply { rows += step }

            .add("${I18n.getString("termora.new-host.general.host")}:").xy(1, rows)
            .add(hostTextField).xy(3, rows)
            .add("${I18n.getString("termora.new-host.general.port")}:").xy(5, rows)
            .add(portTextField).xy(7, rows).apply { rows += step }

            .add("${I18n.getString("termora.new-host.general.username")}:").xy(1, rows)
            .add(usernameTextField).xyw(3, rows, 5).apply { rows += step }

            .add("${I18n.getString("termora.new-host.general.authentication")}:").xy(1, rows)
            .add(authenticationTypeComboBox).xyw(3, rows, 5).apply { rows += step }

            .add("${I18n.getString("termora.new-host.general.password")}:").xy(1, rows)
            .add(passwordPanel).xyw(3, rows, 5).apply { rows += step }

            .add(autoReconnect).xyw(3, rows, 5).apply { rows += step }

            .add("${I18n.getString("termora.new-host.general.remark")}:").xy(1, rows)
            .add(JScrollPane(remarkTextArea).apply { border = FlatTextBorder() })
            .xyw(3, rows, 5).apply { rows += step }

            .build()


        return panel
    }

    private fun switchPasswordComponent() {
        passwordPanel.removeAll()

        if (authenticationTypeComboBox.selectedItem == AuthenticationType.PublicKey) {
            val selectedItem = publicKeyComboBox.selectedItem
            publicKeyComboBox.removeAllItems()
            for (pair in KeyManager.getInstance().getOhKeyPairs(accountOwner.id)) {
                publicKeyComboBox.addItem(pair.id)
            }
            publicKeyComboBox.selectedItem = selectedItem
            passwordPanel.add(
                FormBuilder.create()
                    .layout(FormLayout("default:grow, 4dlu, left:pref", "pref"))
                    .add(publicKeyComboBox).xy(1, 1)
                    .add(chooseKeyBtn).xy(3, 1)
                    .build(), BorderLayout.CENTER
            )
        } else if (authenticationTypeComboBox.selectedItem == AuthenticationType.SSHAgent) {
            passwordPanel.add(sshAgentComboBox, BorderLayout.CENTER)
        } else {
            passwordPanel.add(passwordTextField, BorderLayout.CENTER)
        }
        passwordPanel.revalidate()
        passwordPanel.repaint()
    }
}


