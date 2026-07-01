package app.termora.cli

import app.termora.cli.output.CliOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliOutputTest {
    @Test
    fun `json 模式输出可解析的 ExecResult`() {
        val out = CliOutput(json = true)
        val r = ExecResult(exitCode = 0, stdout = "hi\n", stderr = "", host = "web-01", durationMs = 12)
        val text = out.renderExec(r)
        assertTrue(text.contains("\"exitCode\":0"))
        assertTrue(text.contains("\"host\":\"web-01\""))
    }

    @Test
    fun `人类可读模式输出 stdout 原文`() {
        val out = CliOutput(json = false)
        val r = ExecResult(exitCode = 0, stdout = "hello\n", stderr = "", host = "web-01", durationMs = 12)
        val text = out.renderExec(r)
        assertTrue(text.contains("hello"))
    }

    @Test
    fun `人类可读模式 stdout 与 stderr 同时输出且 stderr 接在 stdout 之后`() {
        val out = CliOutput(json = false)
        // stdout 不以换行结尾，覆盖分隔符补 \n 的拼接分支
        val r = ExecResult(exitCode = 1, stdout = "out-text", stderr = "err-text", host = "web-01", durationMs = 12)
        val text = out.renderExec(r)
        assertTrue(text.contains("out-text"))
        assertTrue(text.contains("err-text"))
        // stderr 必须排在 stdout 之后
        assertTrue(text.indexOf("out-text") < text.indexOf("err-text"))
        // 二者之间补了换行
        assertEquals("out-text\nerr-text", text)
    }
}
