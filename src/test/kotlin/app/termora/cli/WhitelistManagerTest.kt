package app.termora.cli

import app.termora.cli.guard.WhitelistManager
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhitelistManagerTest {
    @Test
    fun `空白名单拒绝一切`() {
        val wm = WhitelistManager { "[]" }
        assertFalse(wm.isAllowed("host-1"))
    }

    @Test
    fun `命中白名单内的主机`() {
        val wm = WhitelistManager { """["host-1","host-2"]""" }
        assertTrue(wm.isAllowed("host-1"))
        assertTrue(wm.isAllowed("host-2"))
        assertFalse(wm.isAllowed("host-3"))
    }

    @Test
    fun `非法或空 JSON 视为空白名单`() {
        assertFalse(WhitelistManager { "" }.isAllowed("host-1"))
        assertFalse(WhitelistManager { "not-json" }.isAllowed("host-1"))
    }
}
