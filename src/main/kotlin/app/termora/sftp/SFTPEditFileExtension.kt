package app.termora.sftp

import app.termora.Disposable
import app.termora.plugin.DispatchThread
import app.termora.plugin.Extension
import org.apache.commons.vfs2.FileObject
import java.awt.Window

interface SFTPEditFileExtension : Extension {

    /**
     * @return 当停止编辑后请销毁
     */
    fun edit(owner: Window, file: FileObject): Disposable

    override fun getDispatchThread(): DispatchThread {
        return DispatchThread.BGT
    }
}