package app.termora.cli

import app.termora.Host
import app.termora.SSHDTest
import app.termora.cli.guard.Auditor
import app.termora.cli.guard.WhitelistManager
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SftpTransferIT : SSHDTest() {

    private fun transfer(h: Host, allow: List<String>): SftpTransfer {
        val auditFile = Files.createTempFile("audit", ".log").toFile()
        return SftpTransfer(
            hostResolver = { id -> if (id == h.id) h else null },
            whitelist = WhitelistManager { allow.joinToString(prefix = "[", postfix = "]") { "\"$it\"" } },
            auditor = Auditor(auditFile),
        )
    }

    @Test
    fun `上传后下载内容一致`() {
        val h = host
        val t = transfer(h, listOf(h.id))

        val local = Files.createTempFile("up", ".txt")
        Files.writeString(local, "hello-sftp")
        val remote = "/tmp/termora-cli-it.txt"

        val up = t.upload(h.id, local.toString(), remote)
        assertTrue(up.ok && up.bytes > 0)

        val back = Files.createTempFile("down", ".txt")
        val down = t.download(h.id, remote, back.toString())
        assertTrue(down.ok)
        assertEquals("hello-sftp", Files.readString(back))
    }

    @Test
    fun `非白名单主机上传被拒绝`() {
        val h = host
        val t = transfer(h, emptyList())
        val local = Files.createTempFile("up", ".txt")
        val e = assertFailsWith<CliError> { t.upload(h.id, local.toString(), "/tmp/x.txt") }
        assertEquals(ExitCodes.FORBIDDEN, e.code)
    }

    @Test
    fun `未知主机报 NOT_FOUND`() {
        val h = host
        val t = transfer(h, listOf(h.id))
        val local = Files.createTempFile("up", ".txt")
        val e = assertFailsWith<CliError> { t.upload("nope", local.toString(), "/tmp/x.txt") }
        assertEquals(ExitCodes.NOT_FOUND, e.code)
    }
}
