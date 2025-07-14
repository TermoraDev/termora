package app.termora.plugin.internal.wsl

import app.termora.*
import app.termora.plugin.internal.AltKeyModifier
import app.termora.plugin.internal.BasicTerminalOption
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.ui.FlatTextBorder
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.lang3.StringUtils
import java.awt.BorderLayout
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*

internal open class WSLHostOptionsPane : OptionsPane() {
    protected val generalOption = GeneralOption()
    protected val terminalOption = BasicTerminalOption().apply {
        showCharsetComboBox = true
        showStartupCommandTextField = true
        showEnvironmentTextArea = true
        init()
    }

    protected val owner: Window get() = SwingUtilities.getWindowAncestor(this)

    init {
        addOption(generalOption)
        addOption(terminalOption)
    }


    open fun getHost(): Host {
        val name = generalOption.nameTextField.text
        val protocol = WSLProtocolProvider.PROTOCOL
        val wsl = generalOption.hostComboBox.selectedItem as WSLDistribution
        val host = wsl.distributionName

        val options = Options.Companion.Default.copy(
            encoding = terminalOption.charsetComboBox.selectedItem as String,
            env = terminalOption.environmentTextArea.text,
            startupCommand = terminalOption.startupCommandTextField.text,
            extras = mutableMapOf(
                "wsl-guid" to wsl.guid, "wsl-flavor" to wsl.flavor,
                "altModifier" to (terminalOption.altModifierComboBox.selectedItem?.toString()
                    ?: AltKeyModifier.EightBit.name),
            )
        )

        return Host(
            name = name,
            protocol = protocol,
            host = host,
            options = options,
            sort = System.currentTimeMillis(),
            remark = generalOption.remarkTextArea.text,
        )
    }

    fun setHost(host: Host) {
        generalOption.nameTextField.text = host.name
        generalOption.hostComboBox.selectedItem = host.host
        generalOption.remarkTextArea.text = host.remark
        generalOption.hostComboBox.selectedItem = null
        terminalOption.startupCommandTextField.text = host.options.startupCommand
        terminalOption.environmentTextArea.text = host.options.env
        terminalOption.charsetComboBox.selectedItem = host.options.encoding

        for (i in 0 until generalOption.hostComboBox.itemCount) {
            if (generalOption.hostComboBox.getItemAt(i).distributionName == host.host) {
                generalOption.hostComboBox.selectedIndex = i
                break
            }
        }

    }

    fun validateFields(): Boolean {
        // general
        return (validateField(generalOption.nameTextField)
                || validateField(generalOption.hostComboBox)).not()
    }

    /**
     * 返回 true 表示有错误
     */
    private fun validateField(textField: JTextField): Boolean {
        if (textField.isEnabled && textField.text.isBlank()) {
            setOutlineError(textField)
            return true
        }
        return false
    }

    /**
     * 返回 true 表示有错误
     */
    private fun validateField(comboBox: JComboBox<*>): Boolean {
        val selectedItem = comboBox.selectedItem
        if (comboBox.isEnabled && (selectedItem == null || (selectedItem is String && selectedItem.isBlank()))) {
            selectOptionJComponent(comboBox)
            comboBox.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR)
            comboBox.requestFocusInWindow()
            return true
        }
        return false
    }

    private fun setOutlineError(textField: JTextField) {
        selectOptionJComponent(textField)
        textField.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR)
        textField.requestFocusInWindow()
    }

    protected inner class GeneralOption : JPanel(BorderLayout()), Option {
        val nameTextField = OutlineTextField(128)
        val hostComboBox = OutlineComboBox<WSLDistribution>()
        val remarkTextArea = FixedLengthTextArea(512)

        init {
            initView()
            initEvents()
        }

        private fun initView() {


            hostComboBox.renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component? {
                    val text = if (value is WSLDistribution) value.distributionName else value
                    val c = super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
                    icon = null
                    if (value is WSLDistribution) {
                        icon = if (StringUtils.containsIgnoreCase(value.flavor, "debian")) {
                            Icons.debian
                        } else if (StringUtils.containsIgnoreCase(value.flavor, "ubuntu")) {
                            Icons.ubuntu
                        } else if (StringUtils.containsIgnoreCase(value.flavor, "fedora")) {
                            Icons.fedora
                        } else if (StringUtils.containsIgnoreCase(value.flavor, "alma")) {
                            Icons.almalinux
                        } else {
                            Icons.linux
                        }
                    }
                    return c
                }
            }

            add(getCenterComponent(), BorderLayout.CENTER)
        }

        private fun initEvents() {
            for (distribution in WSLSupport.getDistributions()) {
                hostComboBox.addItem(distribution)
            }


            addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    SwingUtilities.invokeLater { nameTextField.requestFocusInWindow() }
                    removeComponentListener(this)
                }
            })
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
                "left:pref, $FORM_MARGIN, default:grow",
                "pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref"
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

            var rows = 1
            val step = 2
            val panel = FormBuilder.create().layout(layout).debug(false)
                .add("${I18n.getString("termora.new-host.general.name")}:").xy(1, rows)
                .add(nameTextField).xy(3, rows).apply { rows += step }

                .add("${I18n.getString("termora.new-host.wsl.distribution")}:").xy(1, rows)
                .add(hostComboBox).xy(3, rows).apply { rows += step }

                .add("${I18n.getString("termora.new-host.general.remark")}:").xy(1, rows)
                .add(JScrollPane(remarkTextArea).apply { border = FlatTextBorder() })
                .xy(3, rows).apply { rows += step }

                .build()


            return panel
        }

    }


}