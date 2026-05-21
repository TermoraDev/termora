package app.termora

import org.apache.commons.lang3.LocaleUtils
import java.io.BufferedOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class I18nTest {


    @Test
    fun test_zh_CN() {
        val bundle = ResourceBundle.getBundle("i18n/messages", LocaleUtils.toLocale("zh_CN"))
        assertEquals(bundle.getString("termora.confirm"), "确认")
    }


    @Test
    fun test_zh_TW() {
        val bundle = ResourceBundle.getBundle("i18n/messages", LocaleUtils.toLocale("zh_TW"))
        assertEquals(bundle.getString("termora.confirm"), "確定")
    }

    @Test
    fun test_de_DE() {
        val bundle = ResourceBundle.getBundle("i18n/messages", LocaleUtils.toLocale("de_DE"))
        assertEquals(bundle.getString("termora.settings.appearance.language"), "Sprache")
    }
}
