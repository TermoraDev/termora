package app.termora

import app.termora.keymgr.KeyManagerDialog
import app.termora.keymgr.OhKeyPair
import app.termora.localshell.LocalShellDetect
import app.termora.localshell.LocalShellProvider
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatComboBox
import com.formdev.flatlaf.ui.FlatTextBorder
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.lang3.StringUtils
import java.awt.*
import java.awt.event.*
import java.nio.charset.Charset
import javax.swing.*
import javax.swing.table.DefaultTableModel


open class HostOptionsPane : OptionsPane() {
    protected val tunnelingOption = TunnelingOption()
    protected val terminalOption = TerminalOption()
    protected val generalOption = GeneralOption()
    protected val proxyOption = ProxyOption()
    protected val owner: Window? get() = SwingUtilities.getWindowAncestor(this)

    init {
        addOption(generalOption)
        addOption(proxyOption)
        addOption(tunnelingOption)
        addOption(terminalOption)

        setContentBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8))
    }


    open fun getHost(): Host {
        val name = generalOption.nameTextField.text
        val protocol = generalOption.protocolTypeComboBox.selectedItem as Protocol
        val host = generalOption.hostTextField.text
        val port = (generalOption.portTextField.value ?: 22) as Int
        var authentication = Authentication.No
        var proxy = Proxy.No

        if (generalOption.authenticationTypeComboBox.selectedItem == AuthenticationType.Password) {
            authentication = authentication.copy(
                type = AuthenticationType.Password,
                password = String(generalOption.passwordTextField.password)
            )
        } else if (generalOption.authenticationTypeComboBox.selectedItem == AuthenticationType.PublicKey) {
            val keyPair = generalOption.publicKeyTextField.getClientProperty(OhKeyPair::class) as OhKeyPair?
            authentication = authentication.copy(
                type = AuthenticationType.PublicKey,
                password = keyPair?.id ?: StringUtils.EMPTY
            )
        }

        if (proxyOption.proxyTypeComboBox.selectedItem != ProxyType.No) {
            proxy = proxy.copy(
                type = proxyOption.proxyTypeComboBox.selectedItem as ProxyType,
                host = proxyOption.proxyHostTextField.text,
                username = proxyOption.proxyUsernameTextField.text,
                password = String(proxyOption.proxyPasswordTextField.password),
                port = proxyOption.proxyPortTextField.value as Int,
                authenticationType = proxyOption.proxyAuthenticationTypeComboBox.selectedItem as AuthenticationType,
            )
        }

        val localShell = when (val v = terminalOption.localShellProviderComboBox.selectedItem) {
            LocalShellProvider.FollowGlobal -> null
            is LocalShellProvider.CustomCommandLineArgs -> {
                v.localShell.id.asIdentificationString()
            }

            is LocalShellProvider.Specify -> {
                v.localShell.id.asIdentificationString()
            }

            is String -> {
                LocalShellProvider.CustomCommandLineArgs(CommandLineArgs.of(v)).localShell.id.asIdentificationString()
            }

            else -> {
                null
            }
        }

        val options = Options.Default.copy(
            encoding = terminalOption.charsetComboBox.selectedItem as String,
            env = terminalOption.environmentTextArea.text,
            localShell = localShell,
            startupCommand = terminalOption.startupCommandTextField.text,
            heartbeatInterval = (terminalOption.heartbeatIntervalTextField.value ?: 30) as Int,
        )

        return Host(
            name = name,
            protocol = protocol,
            host = host,
            port = port,
            username = generalOption.usernameTextField.text,
            authentication = authentication,
            proxy = proxy,
            sort = System.currentTimeMillis(),
            remark = generalOption.remarkTextArea.text,
            options = options,
            tunnelings = tunnelingOption.tunnelings
        )
    }

    fun validateFields(): Boolean {
        val host = getHost()

        // general
        if (validateField(generalOption.nameTextField)
            || validateField(generalOption.hostTextField)
        ) {
            return false
        }

        if (host.protocol == Protocol.SSH) {
            if (validateField(generalOption.usernameTextField)) {
                return false
            }
        } else if (host.protocol == Protocol.Local) {
            if (validateField(terminalOption.localShellProviderComboBox)) {
                return false
            }
        }

        if (host.authentication.type == AuthenticationType.Password) {
            if (validateField(generalOption.passwordTextField)) {
                return false
            }
        } else if (host.authentication.type == AuthenticationType.PublicKey) {
            if (validateField(generalOption.publicKeyTextField)) {
                return false
            }
        }

        // proxy
        if (host.proxy.type != ProxyType.No) {
            if (validateField(proxyOption.proxyHostTextField)
            ) {
                return false
            }

            if (host.proxy.authenticationType != AuthenticationType.No) {
                if (validateField(proxyOption.proxyUsernameTextField)
                    || validateField(proxyOption.proxyPasswordTextField)
                ) {
                    return false
                }
            }
        }


        return true
    }

    /**
     * 返回 true 表示有错误
     */
    private fun validateField(textField: JTextField): Boolean {
        if (textField.isEnabled && textField.text.isBlank()) {
            selectOptionJComponent(textField)
            textField.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR)
            textField.requestFocusInWindow()
            return true
        }
        return false
    }

    /**
     * 返回 true 表示有错误
     */
    private fun validateField(comboBox: FlatComboBox<*>): Boolean {
        if (comboBox.isEnabled && comboBox.selectedItem == null || comboBox.selectedItem == "") {
            selectOptionJComponent(comboBox)
            comboBox.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR)
            comboBox.requestFocusInWindow()
            return true
        }
        return false
    }

    protected inner class GeneralOption : JPanel(BorderLayout()), Option {
        val portTextField = PortSpinner()
        val nameTextField = OutlineTextField(128)
        val protocolTypeComboBox = FlatComboBox<Protocol>()
        val usernameTextField = OutlineTextField(128)
        val hostTextField = OutlineTextField(255)
        private val passwordPanel = JPanel(BorderLayout())
        private val chooseKeyBtn = JButton(Icons.greyKey)
        val passwordTextField = OutlinePasswordField(255)
        val publicKeyTextField = OutlineTextField()
        val remarkTextArea = FixedLengthTextArea(512)
        val authenticationTypeComboBox = FlatComboBox<AuthenticationType>()

        init {
            initView()
            initEvents()
        }

        private fun initView() {
            add(getCenterComponent(), BorderLayout.CENTER)

            publicKeyTextField.isEditable = false
            chooseKeyBtn.isFocusable = false

            protocolTypeComboBox.renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    return super.getListCellRendererComponent(
                        list,
                        value.toString().uppercase(),
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

            protocolTypeComboBox.addItem(Protocol.SSH)
            protocolTypeComboBox.addItem(Protocol.Local)

            authenticationTypeComboBox.addItem(AuthenticationType.No)
            authenticationTypeComboBox.addItem(AuthenticationType.Password)
            authenticationTypeComboBox.addItem(AuthenticationType.PublicKey)

            authenticationTypeComboBox.selectedItem = AuthenticationType.Password

            refreshStates()
        }

        private fun initEvents() {
            protocolTypeComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    refreshStates()
                }
            }

            authenticationTypeComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    refreshStates()
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
        }

        private fun chooseKeyPair() {
            val dialog = KeyManagerDialog(
                SwingUtilities.getWindowAncestor(this),
                selectMode = true,
            )
            dialog.pack()
            dialog.setLocationRelativeTo(null)
            dialog.isVisible = true
            if (dialog.ok) {
                val lastKeyPair = dialog.getLasOhKeyPair()
                if (lastKeyPair != null) {
                    publicKeyTextField.putClientProperty(OhKeyPair::class, lastKeyPair)
                    publicKeyTextField.text = lastKeyPair.name
                    publicKeyTextField.outline = null
                }
            }
        }

        private fun refreshStates() {
            hostTextField.isEnabled = true
            portTextField.isEnabled = true
            usernameTextField.isEnabled = true
            authenticationTypeComboBox.isEnabled = true
            passwordTextField.isEnabled = true
            chooseKeyBtn.isEnabled = true
            terminalOption.localShellProviderComboBox.isEnabled = false

            if (protocolTypeComboBox.selectedItem == Protocol.Local) {
                hostTextField.isEnabled = false
                portTextField.isEnabled = false
                usernameTextField.isEnabled = false
                authenticationTypeComboBox.isEnabled = false
                passwordTextField.isEnabled = false
                chooseKeyBtn.isEnabled = false
                terminalOption.localShellProviderComboBox.isEnabled = true
            }

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
                "left:pref, $formMargin, default:grow, $formMargin, pref, $formMargin, default, default:grow",
                "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
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

                .add("${I18n.getString("termora.new-host.general.protocol")}:").xy(1, rows)
                .add(protocolTypeComboBox).xyw(3, rows, 5).apply { rows += step }

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

                .add("${I18n.getString("termora.new-host.general.remark")}:").xy(1, rows)
                .add(JScrollPane(remarkTextArea).apply { border = FlatTextBorder() })
                .xyw(3, rows, 5).apply { rows += step }

                .build()


            return panel
        }

        private fun switchPasswordComponent() {
            passwordPanel.removeAll()

            if (authenticationTypeComboBox.selectedItem == AuthenticationType.PublicKey) {
                passwordPanel.add(
                    FormBuilder.create()
                        .layout(FormLayout("default:grow, 4dlu, left:pref", "pref"))
                        .add(publicKeyTextField).xy(1, 1)
                        .add(chooseKeyBtn).xy(3, 1)
                        .build(), BorderLayout.CENTER
                )
            } else {
                passwordPanel.add(passwordTextField, BorderLayout.CENTER)
            }
            passwordPanel.revalidate()
            passwordPanel.repaint()
        }
    }

    protected inner class ProxyOption : JPanel(BorderLayout()), Option {
        val proxyTypeComboBox = FlatComboBox<ProxyType>()
        val proxyHostTextField = OutlineTextField()
        val proxyPasswordTextField = OutlinePasswordField()
        val proxyUsernameTextField = OutlineTextField()
        val proxyPortTextField = PortSpinner(1080)
        val proxyAuthenticationTypeComboBox = FlatComboBox<AuthenticationType>()


        init {
            initView()
            initEvents()
        }

        private fun initView() {
            add(getCenterComponent(), BorderLayout.CENTER)
            proxyAuthenticationTypeComboBox.renderer = object : DefaultListCellRenderer() {
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

            proxyTypeComboBox.addItem(ProxyType.No)
            proxyTypeComboBox.addItem(ProxyType.HTTP)
            proxyTypeComboBox.addItem(ProxyType.SOCKS5)

            proxyAuthenticationTypeComboBox.addItem(AuthenticationType.No)
            proxyAuthenticationTypeComboBox.addItem(AuthenticationType.Password)

            proxyUsernameTextField.text = "root"

            refreshStates()
        }

        private fun initEvents() {
            proxyTypeComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    refreshStates()
                }
            }
            proxyAuthenticationTypeComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    refreshStates()
                }
            }
        }

        private fun refreshStates() {
            proxyHostTextField.isEnabled = proxyTypeComboBox.selectedItem != ProxyType.No
            proxyPortTextField.isEnabled = proxyHostTextField.isEnabled

            proxyAuthenticationTypeComboBox.isEnabled = proxyHostTextField.isEnabled
            proxyUsernameTextField.isEnabled = proxyAuthenticationTypeComboBox.selectedItem != AuthenticationType.No
            proxyPasswordTextField.isEnabled = proxyUsernameTextField.isEnabled
        }

        override fun getIcon(isSelected: Boolean): Icon {
            return Icons.network
        }

        override fun getTitle(): String {
            return I18n.getString("termora.new-host.proxy")
        }

        override fun getJComponent(): JComponent {
            return this
        }

        private fun getCenterComponent(): JComponent {
            val layout = FormLayout(
                "left:pref, $formMargin, default:grow, $formMargin, pref, $formMargin, default, default:grow",
                "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
            )

            var rows = 1
            val step = 2
            val panel = FormBuilder.create().layout(layout)
                .add("${I18n.getString("termora.new-host.general.protocol")}:").xy(1, rows)
                .add(proxyTypeComboBox).xyw(3, rows, 5).apply { rows += step }

                .add("${I18n.getString("termora.new-host.general.host")}:").xy(1, rows)
                .add(proxyHostTextField).xy(3, rows)
                .add("${I18n.getString("termora.new-host.general.port")}:").xy(5, rows)
                .add(proxyPortTextField).xy(7, rows).apply { rows += step }

                .add("${I18n.getString("termora.new-host.general.authentication")}:").xy(1, rows)
                .add(proxyAuthenticationTypeComboBox).xyw(3, rows, 5).apply { rows += step }

                .add("${I18n.getString("termora.new-host.general.username")}:").xy(1, rows)
                .add(proxyUsernameTextField).xyw(3, rows, 5).apply { rows += step }
                .add("${I18n.getString("termora.new-host.general.password")}:").xy(1, rows)
                .add(proxyPasswordTextField).xyw(3, rows, 5).apply { rows += step }

                .build()


            return panel
        }
    }

    protected inner class TerminalOption : JPanel(BorderLayout()), Option {
        val charsetComboBox = JComboBox<String>()
        val startupCommandTextField = OutlineTextField()
        val heartbeatIntervalTextField = IntSpinner(30, minimum = 3, maximum = Int.MAX_VALUE)
        val environmentTextArea = FixedLengthTextArea(2048)
        val localShellProviderComboBox = FlatComboBox<LocalShellProvider>()


        init {
            initView()
            initEvents()
        }

        private fun initView() {
            add(getCenterComponent(), BorderLayout.CENTER)


            environmentTextArea.setFocusTraversalKeys(
                KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .getDefaultFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)
            )
            environmentTextArea.setFocusTraversalKeys(
                KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
                KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .getDefaultFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS)
            )

            environmentTextArea.rows = 8
            environmentTextArea.lineWrap = true
            environmentTextArea.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)

            for (e in Charset.availableCharsets()) {
                charsetComboBox.addItem(e.key)
            }

            charsetComboBox.selectedItem = "UTF-8"

            localShellProviderComboBox.renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component? {
                    val text = when (value) {
                        LocalShellProvider.FollowGlobal -> {
                            I18n.getString("termora.new-host.terminal.local-shell-provider-follow-global")
                        }

                        LocalShellProvider.EmptyCustomCommandLineArgs -> {
                            I18n.getString("termora.new-host.terminal.local-shell-provider-custom-command-line")
                        }

                        is LocalShellProvider.Specify -> {
                            value.localShell.toString()
                        }

                        else -> value.toString()
                    }
                    return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
                }
            }

            localShellProviderComboBox.addItem(LocalShellProvider.FollowGlobal)
            LocalShellDetect.getSupportAllLocalShell().forEach { localShellProviderComboBox.addItem(it.provider) }
            localShellProviderComboBox.addItem(LocalShellProvider.EmptyCustomCommandLineArgs)
        }

        private fun initEvents() {
            localShellProviderComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    when (localShellProviderComboBox.selectedItem) {
                        LocalShellProvider.EmptyCustomCommandLineArgs -> {
                            // 选择了自定义命令行
                            localShellProviderComboBox.isEditable = true
                            localShellProviderComboBox.selectedItem = ""
                        }

                        is String -> {
                        }

                        LocalShellProvider.FollowGlobal,
                        is LocalShellProvider.Specify -> {
                            localShellProviderComboBox.isEditable = false
                        }
                    }
                }
            }
        }


        override fun getIcon(isSelected: Boolean): Icon {
            return Icons.terminal
        }

        override fun getTitle(): String {
            return I18n.getString("termora.new-host.terminal")
        }

        override fun getJComponent(): JComponent {
            return this
        }

        private fun getCenterComponent(): JComponent {
            val layout = FormLayout(
                "left:pref, $formMargin, default:grow, $formMargin, default:grow",
                "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
            )

            var rows = 1
            val step = 2
            val panel = FormBuilder.create().layout(layout)
                .add("${I18n.getString("termora.new-host.terminal.encoding")}:").xy(1, rows)
                .add(charsetComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.new-host.terminal.heartbeat-interval")}:").xy(1, rows)
                .add(heartbeatIntervalTextField).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.new-host.terminal.local-shell-provider")}:").xy(1, rows)
                .add(localShellProviderComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.new-host.terminal.startup-commands")}:").xy(1, rows)
                .add(startupCommandTextField).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.new-host.terminal.env")}:").xy(1, rows)
                .add(JScrollPane(environmentTextArea).apply { border = FlatTextBorder() }).xy(3, rows)
                .apply { rows += step }
                .build()


            return panel
        }
    }

    protected inner class TunnelingOption : JPanel(BorderLayout()), Option {
        val tunnelings = mutableListOf<Tunneling>()

        private val model = object : DefaultTableModel() {
            override fun getRowCount(): Int {
                return tunnelings.size
            }

            override fun isCellEditable(row: Int, column: Int): Boolean {
                return false
            }

            fun addRow(tunneling: Tunneling) {
                val rowCount = super.getRowCount()
                tunnelings.add(tunneling)
                super.fireTableRowsInserted(rowCount, rowCount + 1)
            }

            override fun getValueAt(row: Int, column: Int): Any {
                val tunneling = tunnelings[row]
                return when (column) {
                    0 -> tunneling.name
                    1 -> tunneling.type
                    2 -> "${tunneling.sourceHost}:${tunneling.sourcePort}"
                    3 -> "${tunneling.destinationHost}:${tunneling.destinationPort}"
                    else -> super.getValueAt(row, column)
                }
            }
        }
        private val table = JTable(model)
        private val addBtn = JButton(I18n.getString("termora.new-host.tunneling.add"))
        private val editBtn = JButton(I18n.getString("termora.new-host.tunneling.edit"))
        private val deleteBtn = JButton(I18n.getString("termora.new-host.tunneling.delete"))

        init {
            initView()
            initEvents()
        }

        private fun initView() {
            val scrollPane = JScrollPane(table)

            model.addColumn(I18n.getString("termora.new-host.tunneling.table.name"))
            model.addColumn(I18n.getString("termora.new-host.tunneling.table.type"))
            model.addColumn(I18n.getString("termora.new-host.tunneling.table.source"))
            model.addColumn(I18n.getString("termora.new-host.tunneling.table.destination"))


            table.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
            table.border = BorderFactory.createEmptyBorder()
            table.fillsViewportHeight = true
            scrollPane.border = BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(4, 0, 4, 0),
                BorderFactory.createMatteBorder(1, 1, 1, 1, DynamicColor.BorderColor)
            )

            deleteBtn.isFocusable = false
            addBtn.isFocusable = false
            editBtn.isFocusable = false

            editBtn.isEnabled = false
            deleteBtn.isEnabled = false

            val box = Box.createHorizontalBox()
            box.add(addBtn)
            box.add(Box.createHorizontalStrut(4))
            box.add(editBtn)
            box.add(Box.createHorizontalStrut(4))
            box.add(deleteBtn)

            add(JLabel("TCP/IP Forwarding:"), BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
            add(box, BorderLayout.SOUTH)

        }

        private fun initEvents() {
            addBtn.addActionListener(object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent?) {
                    val dialog = PortForwardingDialog(SwingUtilities.getWindowAncestor(this@HostOptionsPane))
                    dialog.isVisible = true
                    val tunneling = dialog.tunneling ?: return
                    model.addRow(tunneling)
                }
            })


            editBtn.addActionListener(object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent?) {
                    val row = table.selectedRow
                    if (row < 0) {
                        return
                    }
                    val dialog = PortForwardingDialog(
                        SwingUtilities.getWindowAncestor(this@HostOptionsPane),
                        tunnelings[row]
                    )
                    dialog.isVisible = true
                    tunnelings[row] = dialog.tunneling ?: return
                    model.fireTableRowsUpdated(row, row)
                }
            })

            deleteBtn.addActionListener(object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    val rows = table.selectedRows
                    if (rows.isEmpty()) return
                    rows.sortDescending()
                    for (row in rows) {
                        tunnelings.removeAt(row)
                        model.fireTableRowsDeleted(row, row)
                    }
                }
            })

            table.selectionModel.addListSelectionListener {
                editBtn.isEnabled = table.selectedRowCount > 0
                deleteBtn.isEnabled = editBtn.isEnabled
            }

            table.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount % 2 == 0 && SwingUtilities.isLeftMouseButton(e)) {
                        editBtn.actionListeners.forEach {
                            it.actionPerformed(
                                ActionEvent(
                                    editBtn,
                                    ActionEvent.ACTION_PERFORMED,
                                    StringUtils.EMPTY
                                )
                            )
                        }
                    }
                }
            })
        }

        override fun getIcon(isSelected: Boolean): Icon {
            return Icons.showWriteAccess
        }

        override fun getTitle(): String {
            return I18n.getString("termora.new-host.tunneling")
        }

        override fun getJComponent(): JComponent {
            return this
        }

        private inner class PortForwardingDialog(
            owner: Window,
            var tunneling: Tunneling? = null
        ) : DialogWrapper(owner) {
            private val formMargin = "4dlu"
            private val typeComboBox = FlatComboBox<TunnelingType>()
            private val nameTextField = OutlineTextField(32)
            private val localHostTextField = OutlineTextField()
            private val localPortSpinner = PortSpinner()
            private val remoteHostTextField = OutlineTextField()
            private val remotePortSpinner = PortSpinner()

            init {
                isModal = true
                title = I18n.getString("termora.new-host.tunneling")
                controlsVisible = false

                typeComboBox.addItem(TunnelingType.Local)
                typeComboBox.addItem(TunnelingType.Remote)
                typeComboBox.addItem(TunnelingType.Dynamic)

                localHostTextField.text = "127.0.0.1"
                localPortSpinner.value = 1080

                remoteHostTextField.text = "127.0.0.1"

                typeComboBox.addItemListener {
                    if (it.stateChange == ItemEvent.SELECTED) {
                        remoteHostTextField.isEnabled = typeComboBox.selectedItem != TunnelingType.Dynamic
                        remotePortSpinner.isEnabled = remoteHostTextField.isEnabled
                    }
                }

                tunneling?.let {
                    localHostTextField.text = it.sourceHost
                    localPortSpinner.value = it.sourcePort
                    remoteHostTextField.text = it.destinationHost
                    remotePortSpinner.value = it.destinationPort
                    nameTextField.text = it.name
                    typeComboBox.selectedItem = it.type
                }

                init()
                pack()
                size = Dimension(UIManager.getInt("Dialog.width") - 300, size.height)
                setLocationRelativeTo(null)

            }

            override fun doOKAction() {
                if (nameTextField.text.isBlank()) {
                    nameTextField.outline = "error"
                    nameTextField.requestFocusInWindow()
                    return
                } else if (localHostTextField.text.isBlank()) {
                    localHostTextField.outline = "error"
                    localHostTextField.requestFocusInWindow()
                    return
                } else if (remoteHostTextField.text.isBlank()) {
                    remoteHostTextField.outline = "error"
                    remoteHostTextField.requestFocusInWindow()
                    return
                }

                tunneling = Tunneling(
                    name = nameTextField.text,
                    type = typeComboBox.selectedItem as TunnelingType,
                    sourceHost = localHostTextField.text,
                    sourcePort = localPortSpinner.value as Int,
                    destinationHost = remoteHostTextField.text,
                    destinationPort = remotePortSpinner.value as Int,
                )

                super.doOKAction()
            }

            override fun doCancelAction() {
                tunneling = null
                super.doCancelAction()
            }

            override fun createCenterPanel(): JComponent {
                val layout = FormLayout(
                    "left:pref, $formMargin, default:grow, $formMargin, pref",
                    "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
                )

                var rows = 1
                val step = 2
                return FormBuilder.create().layout(layout).padding("0dlu, $formMargin, $formMargin, $formMargin")
                    .add("${I18n.getString("termora.new-host.tunneling.table.name")}:").xy(1, rows)
                    .add(nameTextField).xyw(3, rows, 3).apply { rows += step }
                    .add("${I18n.getString("termora.new-host.tunneling.table.type")}:").xy(1, rows)
                    .add(typeComboBox).xyw(3, rows, 3).apply { rows += step }
                    .add("${I18n.getString("termora.new-host.tunneling.table.source")}:").xy(1, rows)
                    .add(localHostTextField).xy(3, rows)
                    .add(localPortSpinner).xy(5, rows).apply { rows += step }
                    .add("${I18n.getString("termora.new-host.tunneling.table.destination")}:").xy(1, rows)
                    .add(remoteHostTextField).xy(3, rows)
                    .add(remotePortSpinner).xy(5, rows).apply { rows += step }
                    .build()
            }


        }
    }

}