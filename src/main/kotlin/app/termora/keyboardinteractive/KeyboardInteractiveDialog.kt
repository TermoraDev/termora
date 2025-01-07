package app.termora.keyboardinteractive

import app.termora.DialogWrapper
import app.termora.I18n
import app.termora.OutlinePasswordField
import app.termora.OutlineTextField
import com.formdev.flatlaf.FlatClientProperties
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.lang3.StringUtils
import java.awt.Dimension
import java.awt.Window
import javax.swing.JComponent
import javax.swing.text.JTextComponent

class KeyboardInteractiveDialog(
    owner: Window,
    private val prompt: String,
    echo: Boolean
) : DialogWrapper(owner) {

    private val textField = (if (echo) OutlineTextField() else OutlinePasswordField()) as JTextComponent


    init {
        isModal = true
        isResizable = true
        controlsVisible = false
        title = I18n.getString("termora.new-host.title")

        init()
        pack()
        size = Dimension(300, size.height)
        setLocationRelativeTo(null)

    }

    override fun createCenterPanel(): JComponent {
        val formMargin = "4dlu"
        val layout = FormLayout(
            "left:pref, $formMargin, default:grow",
            "pref, $formMargin, pref, $formMargin"
        )

        var rows = 1
        val step = 2
        return FormBuilder.create().layout(layout).padding("$formMargin, $formMargin, 0, $formMargin")
            .add(prompt).xy(1, rows)
            .add(textField).xy(3, rows).apply { rows += step }
            .build()
    }

    override fun doCancelAction() {
        textField.text = StringUtils.EMPTY
        super.doCancelAction()
    }

    override fun doOKAction() {
        if (textField.text.isBlank()) {
            textField.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR)
            textField.requestFocusInWindow()
            return
        }
        super.doOKAction()
    }

    fun getText(): String {
        isModal = true
        isVisible = true
        return textField.text
    }
}