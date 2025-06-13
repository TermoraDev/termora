package app.termora.database

import app.termora.database.DatabaseManager.Companion.log
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionManager
import javax.swing.SwingUtilities

interface DatabaseChangedExtension : Extension {

    companion object {
        fun fireDataChanged(id: String, type: String, action: Action, source: Source = Source.User) {
            if (SwingUtilities.isEventDispatchThread()) {
                for (extension in ExtensionManager.getInstance().getExtensions(DatabaseChangedExtension::class.java)) {
                    try {
                        extension.onDataChanged(id, type, action, source)
                    } catch (e: Exception) {
                        if (log.isErrorEnabled) {
                            log.error(e.message, e)
                        }
                    }
                }
            } else {
                SwingUtilities.invokeLater { fireDataChanged(id, type, action, source) }
            }
        }
    }

    enum class Action {
        Changed,
        Added,
        Removed
    }

    enum class Source {
        User,
        Sync,
    }

    /**
     * 数据变动 如果 [type] 和 [id] 同时为空，那么不知道删除了什么，所有类型都需要刷新
     *
     * @param type 为空时表示删除
     */
    fun onDataChanged(id: String, type: String, action: Action, source: Source = Source.User) {}

}