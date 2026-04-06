package app.termora

import app.termora.account.AccountOwner
import app.termora.account.TeamRole
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.database.OwnerType
import app.termora.protocol.*
import app.termora.transfer.ScaleIcon
import com.formdev.flatlaf.extras.components.FlatToolBar
import com.formdev.flatlaf.ui.FlatButtonBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.jdesktop.swingx.SwingXUtilities
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Window
import javax.swing.*

class NewHostDialogV2(
    owner: Window,
    private val editHost: Host? = null,
    private val accountOwner: AccountOwner,
) : DialogWrapper(owner) {

    private object Current {
        var card: ProtocolHostPanel? = null
        var extension: ProtocolHostPanelExtension? = null
    }

    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)
    private val testConnectionAction = createTestConnectionAction()
    private val testConnectionBtn = JButton(testConnectionAction)
    private val buttonGroup = mutableListOf<JToggleButton>()
    var host: Host? = null
        private set

    init {

        size = Dimension(UIManager.getInt("Dialog.width"), UIManager.getInt("Dialog.height"))
        isModal = true
        title = I18n.getString("termora.new-host.title")

        setLocationRelativeTo(owner)

        init()
    }


    override fun addNotify() {
        super.addNotify()

        controlsVisible = false
    }

    override fun createCenterPanel(): JComponent {
        val toolbar = FlatToolBar()
        val panel = JPanel(BorderLayout())

        toolbar.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 1, 0, DynamicColor.BorderColor),
            BorderFactory.createEmptyBorder(4, 0, 4, 0)
        )
        panel.add(toolbar, BorderLayout.NORTH)
        panel.add(cardPanel, BorderLayout.CENTER)

        toolbar.add(Box.createHorizontalGlue())

        val extensions = ProtocolHostPanelExtension.extensions
            .filter { it.canCreateProtocolHostPanel(accountOwner) }
        for ((index, extension) in extensions.withIndex()) {
            val protocol = extension.getProtocolProvider().getProtocol()
            val icon = ScaleIcon(extension.getProtocolProvider().getIcon(), 22)
            val hostPanel = extension.createProtocolHostPanel(accountOwner)
            val button = JToggleButton(protocol, icon).apply { buttonGroup.add(this) }
            button.setVerticalTextPosition(SwingConstants.BOTTOM)
            button.setHorizontalTextPosition(SwingConstants.CENTER)
            button.border = BorderFactory.createCompoundBorder(
                FlatButtonBorder(),
                BorderFactory.createEmptyBorder(0, 4, 0, 4)
            )
            button.addActionListener { show(protocol, hostPanel, extension, button) }

            Disposer.register(disposable, hostPanel)

            cardPanel.add(hostPanel, protocol)

            toolbar.add(button)

            if (extension != extensions.last()) {
                toolbar.add(Box.createHorizontalStrut(6))
            }

            if (editHost == null) {
                if (index == 0) {
                    show(protocol, hostPanel, extension, button)
                }
            } else {
                if (StringUtils.equalsIgnoreCase(editHost.protocol, protocol)) {
                    show(protocol, hostPanel, extension, button)
                    Current.card?.setHost(editHost)
                }
            }

        }

        if (editHost != null && Current.card == null) {
            SwingUtilities.invokeLater {
                OptionPane.showMessageDialog(
                    this,
                    I18n.getString("termora.protocol.not-supported", editHost.protocol),
                    messageType = JOptionPane.ERROR_MESSAGE
                )
                doCancelAction()
            }
        }

        toolbar.add(Box.createHorizontalGlue())

        return panel
    }

    private fun show(
        name: String,
        card: ProtocolHostPanel,
        extension: ProtocolHostPanelExtension,
        button: JToggleButton
    ) {
        Current.card?.onBeforeHidden()
        card.onBeforeShown()
        cardLayout.show(cardPanel, name)
        Current.card?.onHidden()
        card.onShown()

        Current.card = card
        Current.extension = extension

        buttonGroup.forEach { it.isSelected = false }
        button.isSelected = true

        val provider = extension.getProtocolProvider()
        testConnectionBtn.isVisible = provider is ProtocolTester

        preventImportantData()
    }

    private fun preventImportantData() {
        if (visitorMode()) {
            for (component in SwingUtils.getDescendantsOfType(JComponent::class.java, cardPanel)) {
                if (component is OutlinePasswordField) {
                    component.styleMap = component.styleMap.toMutableMap().apply {
                        put("showRevealButton", false)
                    }
                }
            }
        }
    }

    private fun visitorMode(): Boolean {
        return accountOwner.isVisitorMode()
    }

    override fun createActions(): List<AbstractAction> {
        val actions = mutableListOf<AbstractAction>()
        if (visitorMode().not()) {
            actions.add(createOkAction())
            actions.add(testConnectionAction)
        }
        actions.add(CancelAction())
        return actions
    }

    override fun createJButtonForAction(action: Action): JButton {
        if (testConnectionAction == action) {
            return testConnectionBtn
        }
        return super.createJButtonForAction(action)
    }

    private fun createTestConnectionAction(): AbstractAction {
        return object : AnAction(I18n.getString("termora.new-host.test-connection")) {
            override fun actionPerformed(evt: AnActionEvent) {

                val panel = Current.card ?: return
                if (panel.validateFields().not()) return
                val host = panel.getHost()
                val provider = ProtocolProvider.valueOf(host.protocol) ?: return
                if (provider !is ProtocolTester) return

                putValue(NAME, "${I18n.getString("termora.new-host.test-connection")}...")
                isEnabled = false

                swingCoroutineScope.launch(Dispatchers.IO) {
                    // 因为测试连接的时候从数据库读取会导致失效，所以这里生成随机ID
                    testConnection(provider, host)
                    withContext(Dispatchers.Swing) {
                        putValue(NAME, I18n.getString("termora.new-host.test-connection"))
                        isEnabled = true
                    }
                }
            }
        }
    }

    private suspend fun testConnection(tester: ProtocolTester, host: Host) {
        try {
            val request = ProtocolTestRequest(host = host, owner = this)
            if (tester.canTestConnection(request))
                tester.testConnection(request)
        } catch (e: Exception) {
            withContext(Dispatchers.Swing) {
                OptionPane.showMessageDialog(
                    owner, ExceptionUtils.getMessage(e),
                    messageType = JOptionPane.ERROR_MESSAGE
                )
            }
            return
        }

        withContext(Dispatchers.Swing) {
            OptionPane.showMessageDialog(
                owner,
                I18n.getString("termora.new-host.test-connection-successful")
            )
        }
    }

    override fun doOKAction() {
        val panel = Current.card ?: return
        if (panel.validateFields().not()) return
        var host = panel.getHost()

        if (editHost != null) {
            val extras = mutableMapOf<String, String>()
            extras.putAll(editHost.options.extras)
            extras.putAll(host.options.extras)

            val tags = mutableListOf<String>()
            tags.addAll(editHost.options.tags)
            tags.addAll(host.options.tags)

            host = editHost.copy(
                name = host.name,
                protocol = host.protocol,
                host = host.host,
                port = host.port,
                username = host.username,
                authentication = host.authentication,
                proxy = host.proxy,
                remark = host.remark,
                options = host.options.copy(extras = extras, tags = tags),
                tunnelings = host.tunnelings,
            )
        }

        this.host = host

        super.doOKAction()
    }

}