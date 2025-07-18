package app.termora.plugin.internal

import app.termora.*
import app.termora.OptionsPane.Companion.FORM_MARGIN
import app.termora.OptionsPane.Option
import app.termora.account.AccountOwner
import app.termora.highlight.KeywordHighlight
import app.termora.highlight.KeywordHighlightManager
import app.termora.highlight.KeywordHighlightType
import app.termora.plugin.internal.telnet.TelnetHostOptionsPane.Backspace
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import com.formdev.flatlaf.ui.FlatTextBorder
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import java.awt.BorderLayout
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.nio.charset.Charset
import javax.swing.*

class BasicTerminalOption() : JPanel(BorderLayout()), Option {

    var showCharsetComboBox: Boolean = false
    var showStartupCommandTextField: Boolean = false
    var showHeartbeatIntervalTextField: Boolean = false
    var showEnvironmentTextArea: Boolean = false
    var showLoginScripts: Boolean = false
    var showBackspaceComboBox: Boolean = false
    var showCharacterAtATimeTextField: Boolean = false
    var showAltModifierComboBox: Boolean = true
    var showHighlightSet: Boolean = true
    var accountOwner: AccountOwner? = null

    val charsetComboBox = JComboBox<String>()
    val startupCommandTextField = OutlineTextField()
    val heartbeatIntervalTextField = IntSpinner(30, minimum = 3, maximum = Int.MAX_VALUE)
    val environmentTextArea = FixedLengthTextArea(2048)
    val loginScripts = mutableListOf<LoginScript>()
    val backspaceComboBox = JComboBox<Backspace>()
    val altModifierComboBox = JComboBox<AltKeyModifier>()
    val highlightSetComboBox = JComboBox<KeywordHighlight>()
    val characterAtATimeTextField = YesOrNoComboBox()


    private val loginScriptPanel = LoginScriptPanel(loginScripts)
    private val tabbed = FlatTabbedPane()

    fun init() {
        initView()
        initEvents()
    }

    private fun initView() {

        if (showLoginScripts) {
            tabbed.styleMap = mapOf(
                "focusColor" to DynamicColor("TabbedPane.background"),
                "hoverColor" to DynamicColor("TabbedPane.background"),
            )
            tabbed.tabHeight = UIManager.getInt("TabbedPane.tabHeight") - 4
            putClientProperty("ContentPanelBorder", BorderFactory.createEmptyBorder())
            tabbed.addTab(I18n.getString("termora.new-host.general"), getCenterComponent())
            tabbed.addTab(I18n.getString("termora.new-host.terminal.login-scripts"), loginScriptPanel)
            add(tabbed, BorderLayout.CENTER)
        } else {
            add(getCenterComponent(), BorderLayout.CENTER)
        }

        if (showAltModifierComboBox) {
            altModifierComboBox.addItem(AltKeyModifier.EightBit)
            altModifierComboBox.addItem(AltKeyModifier.CharactersPrecededByESC)

            altModifierComboBox.renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component? {
                    var text = value?.toString() ?: value
                    if (value == AltKeyModifier.CharactersPrecededByESC) {
                        text = I18n.getString("termora.new-host.terminal.alt-modifier.by-esc")
                    } else if (value == AltKeyModifier.EightBit) {
                        text = I18n.getString("termora.new-host.terminal.alt-modifier.eight-bit")
                    }
                    return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
                }
            }
        }

        val accountOwner = this.accountOwner
        if (showHighlightSet && accountOwner != null) {
            val highlights = KeywordHighlightManager.getInstance()
                .getKeywordHighlights(accountOwner.id)
                .filter { it.type == KeywordHighlightType.Set }
            highlightSetComboBox.addItem(KeywordHighlight(id = "-1", keyword = "None"))
            val defaultHighlight = KeywordHighlight(id = "0", keyword = I18n.getString("termora.highlight.default-set"))
            highlightSetComboBox.addItem(defaultHighlight)
            for (highlight in highlights) {
                highlightSetComboBox.addItem(highlight)
            }
            highlightSetComboBox.selectedItem = defaultHighlight

            highlightSetComboBox.renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component? {
                    var text = value?.toString() ?: value
                    if (value is KeywordHighlight) {
                        text = value.keyword
                    }
                    return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
                }
            }
        }


        if (showBackspaceComboBox) {
            backspaceComboBox.addItem(Backspace.Delete)
            backspaceComboBox.addItem(Backspace.Backspace)
            backspaceComboBox.addItem(Backspace.VT220)
        }

        if (showCharacterAtATimeTextField) {
            characterAtATimeTextField.selectedItem = false
        }

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

    }

    private fun initEvents() {

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
            "left:pref, $FORM_MARGIN, default:grow",
            "pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref"
        )

        val accountOwner = this.accountOwner
        var rows = 1
        val step = 2
        val builder = FormBuilder.create().layout(layout)
        if (showLoginScripts) {
            builder.border(BorderFactory.createEmptyBorder(6, 8, 6, 8))
        }

        if (showCharsetComboBox) {
            builder.add("${I18n.getString("termora.new-host.terminal.encoding")}:").xy(1, rows)
                .add(charsetComboBox).xy(3, rows).apply { rows += step }
        }

        if (showHighlightSet && accountOwner != null) {
            builder.add("${I18n.getString("termora.highlight")}:").xy(1, rows)
                .add(highlightSetComboBox).xy(3, rows).apply { rows += step }
        }

        if (showAltModifierComboBox) {
            builder.add("${I18n.getString("termora.new-host.terminal.alt-modifier")}:").xy(1, rows)
                .add(altModifierComboBox).xy(3, rows).apply { rows += step }
        }

        if (showBackspaceComboBox) {
            builder.add("${I18n.getString("termora.new-host.terminal.backspace")}:").xy(1, rows)
                .add(backspaceComboBox).xy(3, rows).apply { rows += step }
        }

        if (showCharacterAtATimeTextField) {
            builder
                .add("${I18n.getString("termora.new-host.terminal.character-mode")}:").xy(1, rows)
                .add(characterAtATimeTextField).xy(3, rows).apply { rows += step }
        }

        if (showHeartbeatIntervalTextField) {
            builder.add("${I18n.getString("termora.new-host.terminal.heartbeat-interval")}:").xy(1, rows)
                .add(heartbeatIntervalTextField).xy(3, rows).apply { rows += step }
        }

        if (showStartupCommandTextField) {
            builder.add("${I18n.getString("termora.new-host.terminal.startup-commands")}:").xy(1, rows)
                .add(startupCommandTextField).xy(3, rows).apply { rows += step }
        }


        if (showEnvironmentTextArea) {
            builder.add("${I18n.getString("termora.new-host.terminal.env")}:").xy(1, rows)
                .add(JScrollPane(environmentTextArea).apply { border = FlatTextBorder() }).xy(3, rows)
                .apply { rows += step }
        }


        return builder.build()
    }

}
