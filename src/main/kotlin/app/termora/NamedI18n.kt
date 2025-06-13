package app.termora

import org.slf4j.LoggerFactory
import java.util.*

abstract class NamedI18n(private val baseName: String) : AbstractI18n() {
    companion object {
        private val log = LoggerFactory.getLogger(NamedI18n::class.java)
    }

    private val myBundle by lazy {
        val bundle =
            ResourceBundle.getBundle(baseName, Locale.getDefault(), javaClass.classLoader)
        if (log.isInfoEnabled) {
            log.info("I18n: {}", bundle.baseBundleName ?: "null")
        }
        return@lazy bundle
    }

    override fun getBundle(): ResourceBundle {
        return myBundle
    }
}