package app.termora.plugins.editor

import app.termora.DialogWrapper
import app.termora.Disposable
import app.termora.Disposer
import app.termora.OptionPane
import app.termora.sftp.absolutePathString
import org.apache.commons.vfs2.FileObject
import java.awt.Dimension
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.UIManager


class EditorDialog(file: FileObject, owner: Window, myDisposable: Disposable) : DialogWrapper(null) {

    private val filename = file.name.baseName
    private val filepath = File(file.absolutePathString())
    private val editorPanel = EditorPanel(this, filepath)

    init {
        Disposer.register(disposable, myDisposable)

        size = Dimension(UIManager.getInt("Dialog.width"), UIManager.getInt("Dialog.height"))
        isModal = false
        controlsVisible = true
        isResizable = true
        title = filename
        iconImages = owner.iconImages
        escapeDispose = false
        defaultCloseOperation = DO_NOTHING_ON_CLOSE

        initEvents()

        setLocationRelativeTo(owner)

        init()
    }


    private fun initEvents() {
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                doCancelAction()
            }
        })
    }

    override fun doCancelAction() {
        if (editorPanel.changes()) {
            if (OptionPane.showConfirmDialog(
                    this,
                    "文件尚未保存，你确定要退出吗？",
                    optionType = JOptionPane.OK_CANCEL_OPTION,
                ) != JOptionPane.OK_OPTION
            ) {
                return
            }
        }
        super.doCancelAction()
    }

    override fun createCenterPanel(): JComponent {
        return editorPanel
    }

    override fun createSouthPanel(): JComponent? {
        return null
    }
}