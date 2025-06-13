package app.termora.database

import app.termora.database.DatabaseManager.Companion.log
import app.termora.plugin.DispatchThread
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionManager
import javax.swing.SwingUtilities

interface DatabaseReadyExtension : Extension {

    companion object {
        fun fireReady(databaseManager: DatabaseManager) {
            if (SwingUtilities.isEventDispatchThread()) {
                for (extension in ExtensionManager.getInstance().getExtensions(DatabaseReadyExtension::class.java)) {
                    try {
                        extension.ready(databaseManager)
                    } catch (e: Exception) {
                        if (log.isErrorEnabled) {
                            log.error(e.message, e)
                        }
                    }
                }
            } else {
                SwingUtilities.invokeLater { fireReady(databaseManager) }
            }
        }
    }

    /**
     * 数据库初始化完成
     */
    fun ready(databaseManager: DatabaseManager) {}

    override fun getDispatchThread(): DispatchThread {
        return DispatchThread.BGT
    }
}