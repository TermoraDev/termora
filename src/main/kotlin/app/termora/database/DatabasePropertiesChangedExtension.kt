package app.termora.database

import app.termora.database.DatabaseManager.Companion.log
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionManager
import javax.swing.SwingUtilities

internal interface DatabasePropertiesChangedExtension : Extension {

    companion object {
        fun onPropertyChanged(name: String, key: String, value: String) {
            if (SwingUtilities.isEventDispatchThread()) {
                for (extension in ExtensionManager.getInstance()
                    .getExtensions(DatabasePropertiesChangedExtension::class.java)) {
                    try {
                        extension.onPropertyChanged(name, key, value)
                    } catch (e: Exception) {
                        if (log.isErrorEnabled) {
                            log.error(e.message, e)
                        }
                    }
                }
            } else {
                SwingUtilities.invokeLater { onPropertyChanged(name, key, value) }
            }
        }
    }


    /**
     * 属性数据变动
     *
     * @param name 属性名
     * @param key key
     */
    fun onPropertyChanged(name: String, key: String, value: String)

}