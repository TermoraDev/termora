package app.termora.plugin.internal

import app.termora.*
import com.formdev.flatlaf.ui.FlatTextBorder
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import java.awt.BorderLayout
import java.awt.KeyboardFocusManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*

class BasicGeneralOption : JPanel(BorderLayout()), OptionsPane.Option {
    val nameTextField = OutlineTextField(128)
    val remarkTextArea = FixedLengthTextArea(512)
    private val formMargin = "7dlu"

    init {
        initView()
        initEvents()
    }

    private fun initView() {
        add(getCenterComponent(), BorderLayout.CENTER)
    }

    private fun initEvents() {

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
            "left:pref, $formMargin, default:grow",
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


        var rows = 1
        val step = 2
        val panel = FormBuilder.create().layout(layout)
            .add("${I18n.getString("termora.new-host.general.name")}:").xy(1, rows)
            .add(nameTextField).xy(3, rows).apply { rows += step }
            .add("${I18n.getString("termora.new-host.general.remark")}:").xy(1, rows)
            .add(JScrollPane(remarkTextArea).apply { border = FlatTextBorder() })
            .xy(3, rows).apply { rows += step }
            .build()


        return panel
    }

}