package app.termora.plugins.editor

import app.termora.Disposable
import app.termora.Disposer
import app.termora.sftp.SFTPEditFileExtension
import app.termora.sftp.absolutePathString
import org.apache.commons.vfs2.FileObject
import java.awt.Window
import javax.swing.SwingUtilities

class MySFTPEditFileExtension private constructor() : SFTPEditFileExtension {
    companion object {
        val instance = MySFTPEditFileExtension()
    }

    override fun edit(owner: Window, file: FileObject): Disposable {
        val disposable = Disposer.newDisposable()
        SwingUtilities.invokeLater { EditorDialog(file, owner, disposable).isVisible = true }
        return disposable
    }
}