package app.termora

import org.apache.commons.lang3.ArrayUtils
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.awt.desktop.OpenURIEvent
import java.awt.desktop.OpenURIHandler

class OpenURIHandlers private constructor() {

    companion object {
        private val log = LoggerFactory.getLogger(OpenURIHandlers::class.java)
        fun getInstance(): OpenURIHandlers {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(OpenURIHandlers::class) { OpenURIHandlers() }
        }
    }

    private var handlers = emptyArray<OpenURIHandler>()

    init {
        // 监听回调
        if (isSupported()) {
            Desktop.getDesktop().setOpenURIHandler { e -> trigger(e) }
        }
    }

    fun isSupported(): Boolean {
        return Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_URI)
    }

    internal fun trigger(e: OpenURIEvent) {
        for (handler in handlers) {
            try {
                handler.openURI(e)
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            }
        }
    }

    fun register(handler: OpenURIHandler) {
        handlers += handler
    }

    fun unregister(handler: OpenURIHandler) {
        handlers = ArrayUtils.removeElement(handlers, handler)
    }

}