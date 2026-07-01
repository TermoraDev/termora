package app.termora.cli

import app.termora.plugin.internal.cli.WhitelistStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhitelistStoreTest {
    private fun store(initial: String = "[]"): Pair<WhitelistStore, () -> String> {
        var storage = initial
        val s = WhitelistStore(read = { storage }, write = { storage = it })
        return s to { storage }
    }

    @Test
    fun `空白名单 ids 为空`() {
        val (s, _) = store()
        assertTrue(s.ids().isEmpty())
        assertFalse(s.isEnabled("a"))
    }

    @Test
    fun `启用与停用单个主机`() {
        val (s, raw) = store()
        s.setEnabled("a", true)
        assertTrue(s.isEnabled("a"))
        assertTrue(raw().contains("\"a\""))
        s.setEnabled("a", false)
        assertFalse(s.isEnabled("a"))
    }

    @Test
    fun `启用幂等且保留其它项`() {
        val (s, _) = store("""["a"]""")
        s.setEnabled("b", true)
        s.setEnabled("b", true) // 幂等
        assertEquals(setOf("a", "b"), s.ids())
    }

    @Test
    fun `非法 JSON 视为空白名单`() {
        val (s, _) = store("not-json")
        assertTrue(s.ids().isEmpty())
        s.setEnabled("a", true) // 从空集恢复写入
        assertEquals(setOf("a"), s.ids())
    }
}
