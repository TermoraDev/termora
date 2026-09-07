package app.termora.plugin.internal.cli

import org.apache.commons.lang3.SystemUtils
import java.io.File

/**
 * termora-cli 的 Skill（SKILL.md）安装器。
 * SKILL.md 作为资源打进 jar（classpath `/skill/termora-cli/SKILL.md`），是唯一来源。
 * 安装即把它写到目标工具的 skills 目录：<baseDir>/skills/termora-cli/SKILL.md。
 * Claude Code 与 Codex CLI 都从该结构加载（同一 SKILL.md，不同 baseDir）。
 */
object SkillInstaller {
    const val SKILL_NAME = "termora-cli"
    private const val RESOURCE = "/skill/termora-cli/SKILL.md"

    /** 读取打包的 SKILL.md 文本 */
    fun readSkill(): String {
        val stream = SkillInstaller::class.java.getResourceAsStream(RESOURCE)
            ?: throw IllegalStateException("SKILL resource not found: $RESOURCE")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /** 写到 <baseDir>/skills/termora-cli/SKILL.md，覆盖已存在文件，返回写入的文件 */
    fun installTo(baseDir: File): File {
        val dir = File(baseDir, "skills/$SKILL_NAME")
        dir.mkdirs()
        val file = File(dir, "SKILL.md")
        file.writeText(readSkill(), Charsets.UTF_8)
        return file
    }

    /** Claude Code 个人 skills 根目录 ~/.claude */
    fun claudeBaseDir(): File = File(SystemUtils.getUserHome(), ".claude")

    /** Codex CLI 个人 skills 根目录 ~/.codex */
    fun codexBaseDir(): File = File(SystemUtils.getUserHome(), ".codex")
}
