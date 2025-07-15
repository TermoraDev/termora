package app.termora.plugins.serial

import app.termora.*
import app.termora.plugin.internal.AltKeyModifier
import app.termora.plugin.internal.BasicGeneralOption
import app.termora.plugin.internal.BasicTerminalOption
import com.fazecast.jSerialComm.SerialPort
import com.formdev.flatlaf.FlatClientProperties
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*

class SerialHostOptionsPane : OptionsPane() {
    private val generalOption = BasicGeneralOption()
    private val terminalOption = BasicTerminalOption().apply {
        showCharsetComboBox = true
        showStartupCommandTextField = true
        init()
    }
    private val serialCommOption = SerialCommOption()

    init {
        addOption(generalOption)
        addOption(terminalOption)
        addOption(serialCommOption)

    }


    fun getHost(): Host {
        val name = generalOption.nameTextField.text
        val protocol = SerialProtocolProvider.PROTOCOL

        val serialComm = SerialComm(
            port = serialCommOption.serialPortComboBox.selectedItem?.toString() ?: StringUtils.EMPTY,
            baudRate = serialCommOption.baudRateComboBox.selectedItem?.toString()?.toIntOrNull() ?: 9600,
            dataBits = serialCommOption.dataBitsComboBox.selectedItem as Int? ?: 8,
            stopBits = serialCommOption.stopBitsComboBox.selectedItem as String? ?: "1",
            parity = serialCommOption.parityComboBox.selectedItem as SerialCommParity,
            flowControl = serialCommOption.flowControlComboBox.selectedItem as SerialCommFlowControl
        )

        val options = Options.Companion.Default.copy(
            encoding = terminalOption.charsetComboBox.selectedItem as String,
            startupCommand = terminalOption.startupCommandTextField.text,
            serialComm = serialComm,
            extras = mutableMapOf(
                "altModifier" to (terminalOption.altModifierComboBox.selectedItem?.toString()
                    ?: AltKeyModifier.EightBit.name),
            )
        )

        return Host(
            name = name,
            protocol = protocol,
            sort = System.currentTimeMillis(),
            remark = generalOption.remarkTextArea.text,
            options = options,
        )
    }

    fun setHost(host: Host) {
        generalOption.nameTextField.text = host.name
        generalOption.remarkTextArea.text = host.remark

        terminalOption.charsetComboBox.selectedItem = host.options.encoding
        terminalOption.startupCommandTextField.text = host.options.startupCommand

        val serialComm = host.options.serialComm
        if (serialComm.port.isNotBlank()) {
            serialCommOption.serialPortComboBox.selectedItem = serialComm.port
        }
        serialCommOption.baudRateComboBox.selectedItem = serialComm.baudRate
        serialCommOption.dataBitsComboBox.selectedItem = serialComm.dataBits
        serialCommOption.parityComboBox.selectedItem = serialComm.parity
        serialCommOption.stopBitsComboBox.selectedItem = serialComm.stopBits
        serialCommOption.flowControlComboBox.selectedItem = serialComm.flowControl

        val altModifier = host.options.extras["altModifier"] ?: AltKeyModifier.EightBit.name
        terminalOption.altModifierComboBox.selectedItem = runCatching { AltKeyModifier.valueOf(altModifier) }
            .getOrNull() ?: AltKeyModifier.EightBit
    }

    fun validateFields(): Boolean {
        val host = getHost()

        if (validateField(generalOption.nameTextField)) {
            return false
        }

        if (StringUtils.equalsIgnoreCase(host.protocol, SerialProtocolProvider.PROTOCOL)) {
            if (validateField(serialCommOption.serialPortComboBox)
                || validateField(serialCommOption.baudRateComboBox)
            ) {
                return false
            }
        }

        return true
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

    private fun setOutlineError(textField: JTextField) {
        selectOptionJComponent(textField)
        textField.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR)
        textField.requestFocusInWindow()
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


    protected inner class SerialCommOption : JPanel(BorderLayout()), Option {
        val serialPortComboBox = OutlineComboBox<String>()
        val baudRateComboBox = OutlineComboBox<Int>()
        val dataBitsComboBox = OutlineComboBox<Int>()
        val parityComboBox = OutlineComboBox<SerialCommParity>()
        val stopBitsComboBox = OutlineComboBox<String>()
        val flowControlComboBox = OutlineComboBox<SerialCommFlowControl>()


        init {
            initView()
            initEvents()
        }

        private fun initView() {

            serialPortComboBox.isEditable = true

            baudRateComboBox.isEditable = true
            baudRateComboBox.addItem(9600)
            baudRateComboBox.addItem(19200)
            baudRateComboBox.addItem(38400)
            baudRateComboBox.addItem(57600)
            baudRateComboBox.addItem(115200)

            dataBitsComboBox.addItem(5)
            dataBitsComboBox.addItem(6)
            dataBitsComboBox.addItem(7)
            dataBitsComboBox.addItem(8)
            dataBitsComboBox.selectedItem = 8

            parityComboBox.addItem(SerialCommParity.None)
            parityComboBox.addItem(SerialCommParity.Even)
            parityComboBox.addItem(SerialCommParity.Odd)
            parityComboBox.addItem(SerialCommParity.Mark)
            parityComboBox.addItem(SerialCommParity.Space)

            stopBitsComboBox.addItem("1")
            stopBitsComboBox.addItem("1.5")
            stopBitsComboBox.addItem("2")
            stopBitsComboBox.selectedItem = "1"

            flowControlComboBox.addItem(SerialCommFlowControl.None)
            flowControlComboBox.addItem(SerialCommFlowControl.RTS_CTS)
            flowControlComboBox.addItem(SerialCommFlowControl.XON_XOFF)

            flowControlComboBox.renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    val text = value?.toString() ?: StringUtils.EMPTY
                    return super.getListCellRendererComponent(
                        list,
                        text.replace('_', '/'),
                        index,
                        isSelected,
                        cellHasFocus
                    )
                }
            }

            add(getCenterComponent(), BorderLayout.CENTER)
        }

        private fun initEvents() {
            addComponentListener(object : ComponentAdapter() {
                override fun componentShown(e: ComponentEvent) {
                    removeComponentListener(this)
                    swingCoroutineScope.launch(Dispatchers.IO) {
                        for (commPort in SerialPort.getCommPorts()) {
                            withContext(Dispatchers.Swing) {
                                serialPortComboBox.addItem(commPort.systemPortName)
                            }
                        }
                    }
                }
            })
        }

        override fun getIcon(isSelected: Boolean): Icon {
            return Icons.serial
        }

        override fun getTitle(): String {
            return I18n.getString("termora.new-host.serial")
        }

        override fun getJComponent(): JComponent {
            return this
        }

        private fun getCenterComponent(): JComponent {
            val layout = FormLayout(
                "left:pref, $FORM_MARGIN, default:grow",
                "pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref"
            )

            var rows = 1
            val step = 2
            val panel = FormBuilder.create().layout(layout)
                .add("${I18n.getString("termora.new-host.serial.port")}:").xy(1, rows)
                .add(serialPortComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.new-host.serial.baud-rate")}:").xy(1, rows)
                .add(baudRateComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.new-host.serial.data-bits")}:").xy(1, rows)
                .add(dataBitsComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.new-host.serial.parity")}:").xy(1, rows)
                .add(parityComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.new-host.serial.stop-bits")}:").xy(1, rows)
                .add(stopBitsComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.new-host.serial.flow-control")}:").xy(1, rows)
                .add(flowControlComboBox).xy(3, rows).apply { rows += step }
                .build()
            return panel
        }
    }

}