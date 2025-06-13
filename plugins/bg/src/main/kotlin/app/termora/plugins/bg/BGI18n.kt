package app.termora.plugins.bg

import app.termora.AbstractI18n
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

object BGI18n : AbstractI18n() {
    private val log = LoggerFactory.getLogger(BGI18n::class.java)
    private val myBundle by lazy {
        val bundle = ResourceBundle.getBundle("i18n/messages", Locale.getDefault(), BGI18n::class.java.classLoader)
        if (log.isInfoEnabled) {
            log.info("I18n: {}", bundle.baseBundleName ?: "null")
        }
        return@lazy bundle
    }


    override fun getBundle(): ResourceBundle {
        return myBundle
    }

    override fun getLogger(): Logger {
        return log
    }
}