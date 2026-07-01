package app.termora.cli

import app.termora.plugin.internal.cli.SkillInstaller
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillInstallerTest {
    @Test
    fun `读取打包在 jar 里的 SKILL`() {
        val text = SkillInstaller.readSkill()
        assertTrue(text.contains("name: termora-cli"))
        assertTrue(text.contains("termora-cli exec"))
    }

    @Test
    fun `安装到指定目录写出 skills termora-cli SKILL`() {
        val base = Files.createTempDirectory("install-test").toFile()
        val file = SkillInstaller.installTo(base)

        assertEquals("SKILL.md", file.name)
        // 父目录应为 <base>/skills/termora-cli
        val parent = file.parentFile.path.replace('\\', '/')
        assertTrue(parent.endsWith("skills/termora-cli"), "实际父目录: $parent")
        // 内容与资源一致
        assertEquals(SkillInstaller.readSkill(), file.readText())
    }

    @Test
    fun `重复安装覆盖且内容一致`() {
        val base = Files.createTempDirectory("install-test").toFile()
        SkillInstaller.installTo(base)
        val file = SkillInstaller.installTo(base) // 第二次
        assertEquals(SkillInstaller.readSkill(), file.readText())
    }
}
