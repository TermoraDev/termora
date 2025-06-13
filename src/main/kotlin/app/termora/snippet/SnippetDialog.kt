package app.termora.snippet

import app.termora.DialogWrapper
import app.termora.Disposer
import app.termora.I18n
import app.termora.database.DatabaseManager
import java.awt.Dimension
import java.awt.Window
import javax.swing.JComponent
import javax.swing.UIManager

class SnippetDialog(owner: Window) : DialogWrapper(owner) {
    private val properties get() = DatabaseManager.getInstance().properties

    init {
        initViews()
        initEvents()
        init()
    }

    private fun initViews() {
        size = Dimension(UIManager.getInt("Dialog.width"), UIManager.getInt("Dialog.height"))
        isModal = true
        isResizable = true
        title = I18n.getString("termora.snippet.title")
        setLocationRelativeTo(owner)
    }

    private fun initEvents() {

    }

    override fun createCenterPanel(): JComponent {
        return SnippetPanel().apply { Disposer.register(disposable, this) }
    }

    override fun createSouthPanel(): JComponent? {
        return null
    }
}