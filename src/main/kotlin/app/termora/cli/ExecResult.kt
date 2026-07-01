package app.termora.cli

import kotlinx.serialization.Serializable

@Serializable
data class ExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val host: String,
    // 总耗时（建连 + 执行）由 SshExecutor.execute 统一填入；默认 0 仅作占位。
    val durationMs: Long = 0,
)
