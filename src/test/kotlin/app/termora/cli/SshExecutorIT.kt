package app.termora.cli

import app.termora.Host
import app.termora.SSHDTest
import app.termora.cli.guard.Auditor
import app.termora.cli.guard.DangerousCommandFilter
import app.termora.cli.guard.WhitelistManager
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SshExecutorIT : SSHDTest() {

    // 注意：SSHDTest.host 是 get() 计算属性，每次访问都会 new 一个 id 随机的 Host。
    // 因此每个测试只取一次实例（val h = host）并贯穿始终，保证白名单 id 与 execute 传入的 id 是同一个。
    private fun newExecutor(h: Host): SshExecutor {
        val auditFile = Files.createTempFile("audit", ".log").toFile()
        return SshExecutor(
            hostResolver = { id -> if (id == h.id) h else null },
            whitelist = WhitelistManager { """["${h.id}"]""" },
            dangerous = DangerousCommandFilter(),
            auditor = Auditor(auditFile),
        )
    }

    @Test
    fun `执行 echo 拿到 stdout 与退出码 0`() {
        val h = host
        val r = newExecutor(h).execute(h.id, "echo hello")
        assertEquals(0, r.exitCode)
        assertTrue(r.stdout.contains("hello"))
    }

    @Test
    fun `执行失败命令拿到非零退出码`() {
        val h = host
        val r = newExecutor(h).execute(h.id, "exit 7")
        assertEquals(7, r.exitCode)
    }

    @Test
    fun `非白名单主机被拒绝`() {
        val h = host
        val auditFile = Files.createTempFile("audit", ".log").toFile()
        val exec = SshExecutor(
            hostResolver = { h },
            whitelist = WhitelistManager { "[]" },
            dangerous = DangerousCommandFilter(),
            auditor = Auditor(auditFile),
        )
        val e = assertFailsWith<CliError> { exec.execute(h.id, "echo hi") }
        assertEquals(ExitCodes.FORBIDDEN, e.code)
    }

    @Test
    fun `危险命令被拦截`() {
        val h = host
        val e = assertFailsWith<CliError> { newExecutor(h).execute(h.id, "rm -rf /") }
        assertEquals(ExitCodes.DANGEROUS, e.code)
    }

    @Test
    fun `未知主机报 NOT_FOUND`() {
        val h = host
        val e = assertFailsWith<CliError> { newExecutor(h).execute("nope", "echo hi") }
        assertEquals(ExitCodes.NOT_FOUND, e.code)
    }
}
