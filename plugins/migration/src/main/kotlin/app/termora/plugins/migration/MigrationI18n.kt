package app.termora.plugins.migration

import app.termora.NamedI18n
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object MigrationI18n : NamedI18n("i18n/messages") {
    private val log = LoggerFactory.getLogger(MigrationI18n::class.java)

    override fun getLogger(): Logger {
        return log
    }
}