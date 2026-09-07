package app.termora.cli

import app.termora.plugin.internal.cli.PathInstaller
import com.formdev.flatlaf.util.SystemInfo
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class PathInstallerTest {
    @Test
    fun `生成 wrapper 含 java 与 CLI 入口`() {
        val dir = Files.createTempDirectory("bin").toFile()
        val wrapper = PathInstaller.generateWrapper(dir)

        assertTrue(wrapper.exists())
        val expectedName = if (SystemInfo.isWindows) "termora-cli.cmd" else "termora-cli"
        assertTrue(wrapper.name == expectedName, "实际: ${wrapper.name}")

        val text = wrapper.readText()
        assertTrue(text.contains("app.termora.cli.CliMainKt"), "wrapper 应调用 CLI 入口")
        assertTrue(text.contains("java"), "wrapper 应引用 java")
    }
}
