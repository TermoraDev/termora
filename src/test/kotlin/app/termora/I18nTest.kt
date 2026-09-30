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

    @Test
    fun test_zh_CN_via_default_locale() {
        val original = Locale.getDefault()
        try {
            // I18n 必须跟随 Locale.setDefault 动态变化，
            // 否则应用启动时 loadSettings() 设置在首次访问 I18n 之后，
            // 修改语言后界面会一直显示英文
            Locale.setDefault(LocaleUtils.toLocale("zh_CN"))
            assertEquals(I18n.getString("termora.confirm"), "确认")
            Locale.setDefault(LocaleUtils.toLocale("de_DE"))
            assertEquals(I18n.getString("termora.confirm"), "Bestätigen")
        } finally {
            Locale.setDefault(original)
        }
    }
}
