package app.termora.cli

import app.termora.cli.output.CliOutput
import kotlin.test.Test
import kotlin.test.assertTrue

class CliOutputAssetsTest {
    private val assets = listOf(
        AssetInfo("a", "web-01", "SSH", "10.0.0.1", 22, "root", "生产"),
        AssetInfo("b", "db-01", "SSH", "10.0.0.2", 2222, "admin", ""),
    )

    @Test
    fun `assets json 模式输出 assets 数组`() {
        val text = CliOutput(json = true).renderAssets(assets)
        assertTrue(text.contains("\"assets\""))
        assertTrue(text.contains("\"id\":\"a\""))
        assertTrue(text.contains("\"name\":\"web-01\""))
        // 不含凭证字段
        assertTrue(!text.contains("password") && !text.contains("authentication"))
    }

    @Test
    fun `assets 人类可读模式每行含 name 与 hostport`() {
        val text = CliOutput(json = false).renderAssets(assets)
        assertTrue(text.contains("web-01"))
        assertTrue(text.contains("10.0.0.1:22"))
        assertTrue(text.contains("db-01"))
    }

    @Test
    fun `transfer json 模式输出 ok 与 bytes`() {
        val r = TransferResult(ok = true, bytes = 123, host = "web-01")
        val text = CliOutput(json = true).renderTransfer(r)
        assertTrue(text.contains("\"ok\":true"))
        assertTrue(text.contains("\"bytes\":123"))
    }
}
