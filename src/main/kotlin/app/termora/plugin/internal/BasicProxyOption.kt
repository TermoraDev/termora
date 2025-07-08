package app.termora.plugin.internal

import app.termora.*
import app.termora.OptionsPane.Option
import com.formdev.flatlaf.extras.components.FlatComboBox
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ItemEvent
import javax.swing.*

class BasicProxyOption(
    private val proxyTypes: List<ProxyType> = listOf(ProxyType.HTTP, ProxyType.SOCKS5),
    private val authenticationTypes: List<AuthenticationType> = listOf(AuthenticationType.Password),
) :
    JPanel(BorderLayout()), Option {
    private val formMargin = "7dlu"

    val proxyTypeComboBox = FlatComboBox<ProxyType>()
    val proxyHostTextField = OutlineTextField()
    val proxyPasswordTextField = OutlinePasswordField()
    val proxyUsernameTextField = OutlineTextField()
    val proxyPortTextField = PortSpinner(1080)
    val proxyAuthenticationTypeComboBox = FlatComboBox<AuthenticationType>()

    constructor(proxyTypes: List<ProxyType> = listOf(ProxyType.HTTP, ProxyType.SOCKS5)) : this(
        proxyTypes,
        listOf(AuthenticationType.Password)
    )

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
        for (type in proxyTypes) {
            proxyTypeComboBox.addItem(type)
        }

        proxyAuthenticationTypeComboBox.addItem(AuthenticationType.No)
        for (type in authenticationTypes) {
            proxyAuthenticationTypeComboBox.addItem(type)
        }

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
            "left:pref, $formMargin, default:grow, $formMargin, pref, $formMargin, default",
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
