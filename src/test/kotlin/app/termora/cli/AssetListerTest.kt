package app.termora.cli

import app.termora.Host
import app.termora.cli.guard.WhitelistManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssetListerTest {
    private fun host(id: String, name: String, remark: String = "") =
        Host(id = id, name = name, protocol = "SSH", host = "10.0.0.1", port = 22, username = "root", remark = remark)

    private fun lister(hosts: List<Host>, allow: List<String>) = AssetLister(
        hostsProvider = { hosts },
        whitelist = WhitelistManager { allow.joinToString(prefix = "[", postfix = "]") { "\"$it\"" } },
    )

    @Test
    fun `只列白名单内的主机`() {
        val hosts = listOf(host("a", "web-01"), host("b", "db-01"))
        val result = lister(hosts, listOf("a")).list()
        assertEquals(1, result.size)
        assertEquals("web-01", result[0].name)
        assertEquals("a", result[0].id)
    }

    @Test
    fun `过滤文件夹与已删除节点`() {
        val folder = Host(id = "f", name = "组", protocol = "Folder")
        val deleted = host("d", "old").copy(deleted = true)
        val ok = host("a", "web-01")
        val result = lister(listOf(folder, deleted, ok), listOf("f", "d", "a")).list()
        assertEquals(listOf("web-01"), result.map { it.name })
    }

    @Test
    fun `关键字匹配 name host remark 且忽略大小写`() {
        val hosts = listOf(
            host("a", "web-01", remark = "生产"),
            host("b", "db-01", remark = "测试"),
        )
        val result = lister(hosts, listOf("a", "b")).list("WEB")
        assertEquals(listOf("web-01"), result.map { it.name })
        assertEquals(listOf("db-01"), lister(hosts, listOf("a", "b")).list("测试").map { it.name })
    }

    @Test
    fun `不泄露凭证字段`() {
        // AssetInfo 没有 authentication/password 字段——编译期即保证；这里确认映射只取安全字段
        val result = lister(listOf(host("a", "web-01")), listOf("a")).list()
        assertTrue(result[0].username == "root" && result[0].host == "10.0.0.1")
    }
}
