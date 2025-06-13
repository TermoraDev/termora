package app.termora

import org.apache.commons.lang3.LocaleUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

object I18n : AbstractI18n() {
    private val log = LoggerFactory.getLogger(I18n::class.java)
    private val myBundle by lazy {
        val bundle = ResourceBundle.getBundle("i18n/messages", Locale.getDefault())
        if (log.isInfoEnabled) {
            log.info("I18n: {}", bundle.baseBundleName ?: "null")
        }
        return@lazy bundle
    }

    private val supportedLanguages = sortedMapOf(
        "en_US" to "English",
        "zh_CN" to "简体中文",
        "zh_TW" to "繁體中文",
    )

    fun containsLanguage(locale: Locale): String? {
        for (key in supportedLanguages.keys) {
            val e = LocaleUtils.toLocale(key)
            if (LocaleUtils.toLocale(key) == locale ||
                (e.language.equals(locale.language, true) && e.country.equals(locale.country, true))
            ) {
                return key
            }
        }
        return null
    }

    fun getLanguages(): Map<String, String> {
        return supportedLanguages
    }


    override fun getBundle(): ResourceBundle {
        return myBundle
    }

    override fun getLogger(): Logger {
        return log
    }


}