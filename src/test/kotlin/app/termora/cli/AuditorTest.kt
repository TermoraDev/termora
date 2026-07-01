package app.termora.cli

import app.termora.cli.guard.Auditor
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuditorTest {
    @Test
    fun `追加两行审计记录`() {
        val file = Files.createTempFile("cli-audit", ".log").toFile()
        val auditor = Auditor(file)

        auditor.record("h1", "web-01", "exec", "ls", 0)
        auditor.record("h1", "web-01", "exec", "rm -rf /", 4)

        val lines = file.readLines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("\"command\":\"ls\""))
        assertTrue(lines[1].contains("\"exitCode\":4"))
    }
}
