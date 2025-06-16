package app.termora.plugins.sync

import app.termora.I18n
import app.termora.NamedI18n
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

object SyncI18n : NamedI18n("i18n/messages") {
    private val log = LoggerFactory.getLogger(SyncI18n::class.java)

    override fun getLogger(): Logger {
        return log
    }

    override fun getString(key: String): String {
        return try {
            substitutor.replace(getBundle().getString(key))
        } catch (_: MissingResourceException) {
            I18n.getString(key)
        }
    }

}