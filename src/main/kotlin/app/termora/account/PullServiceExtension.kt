package app.termora.account

import app.termora.database.DatabaseManager.Companion.log
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionManager
import javax.swing.SwingUtilities

interface PullServiceExtension : Extension {

    companion object {
        fun firePullStarted() {
            fire { it.onPullStarted() }
        }

        fun firePullFinished(count: Int) {
            fire { it.onPullFinished(count) }
        }

        private fun fire(invoker: (PullServiceExtension) -> Unit) {
            if (SwingUtilities.isEventDispatchThread()) {
                for (extension in ExtensionManager.getInstance().getExtensions(PullServiceExtension::class.java)) {
                    try {
                        invoker.invoke(extension)
                    } catch (e: Exception) {
                        if (log.isErrorEnabled) {
                            log.error(e.message, e)
                        }
                    }
                }
            } else {
                SwingUtilities.invokeLater { fire(invoker) }
            }
        }
    }

    fun onPullStarted() {}

    /**
     * 同步了多少条数据
     */
    fun onPullFinished(count: Int) {}

}