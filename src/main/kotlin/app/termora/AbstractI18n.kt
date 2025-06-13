package app.termora

import org.apache.commons.text.StringSubstitutor
import org.slf4j.Logger
import java.text.MessageFormat
import java.util.*

abstract class AbstractI18n {
    private val log get() = getLogger()

    private val substitutor by lazy { StringSubstitutor { key -> getString(key) } }

    fun getString(key: String, vararg args: Any): String {
        val text = getString(key)
        if (args.isNotEmpty()) {
            return MessageFormat.format(text, *args)
        }
        return text
    }


    fun getString(key: String): String {
        try {
            return substitutor.replace(getBundle().getString(key))
        } catch (e: MissingResourceException) {
            if (log.isWarnEnabled) {
                log.warn(e.message, e)
            }
            return key
        }
    }


    protected abstract fun getBundle(): ResourceBundle

    protected abstract fun getLogger(): Logger
}