package app.termora.cli

import app.termora.Host
import app.termora.cli.guard.Auditor
import app.termora.cli.guard.DangerousCommandFilter
import app.termora.cli.guard.WhitelistManager
import app.termora.plugin.internal.ssh.SshClients
import org.apache.commons.io.IOUtils
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.session.ClientSession
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.util.EnumSet

/**
 * 组合护栏 + SSH exec：白名单 → 危险命令拦截 → openClient/openSession → exec（收 stdout+stderr+exitCode）→ 审计。
 * 全程不弹 GUI（用不带 Window 的 openClient）；任何失败转 CliError。
 *
 * @param hostResolver 按 id 取 Host；生产用 HostManager.getInstance()::getHost
 * @param timeout 仅作用于命令**执行阶段**（exec channel 的 open/waitFor）；建连与认证阶段的超时
 *                由目标主机的 `host.options.extras["timeout"]`（默认 60s）控制，不受此参数约束。
 */
class SshExecutor(
    private val hostResolver: (String) -> Host?,
    private val whitelist: WhitelistManager,
    private val dangerous: DangerousCommandFilter,
    private val auditor: Auditor,
    private val timeout: Duration = Duration.ofSeconds(60),
) {
    fun execute(assetId: String, command: String): ExecResult {
        val host = hostResolver(assetId)
            ?: throw CliError(ExitCodes.NOT_FOUND, "主机不存在: $assetId")

        if (!whitelist.isAllowed(assetId)) {
            auditor.record(assetId = assetId, host = host.host, command = command, exitCode = ExitCodes.FORBIDDEN)
            throw CliError(ExitCodes.FORBIDDEN, "主机未授权 AI 访问（不在白名单）: ${host.name}")
        }

        dangerous.match(command)?.let { rule ->
            auditor.record(assetId = assetId, host = host.host, command = command, exitCode = ExitCodes.DANGEROUS)
            throw CliError(ExitCodes.DANGEROUS, "危险命令被拦截（规则 $rule）: $command")
        }

        val t0 = System.currentTimeMillis()
        val result = try {
            val client = SshClients.openClient(host)
            try {
                val session = SshClients.openSession(host, client)
                exec(session, command, host.name)
            } finally {
                IOUtils.closeQuietly(client)
            }
        } catch (e: CliError) {
            throw e
        } catch (e: Exception) {
            auditor.record(assetId = assetId, host = host.host, command = command, exitCode = ExitCodes.CONNECT_FAIL)
            throw CliError(ExitCodes.CONNECT_FAIL, "连接或执行失败: ${e.message}", e)
        }

        auditor.record(assetId = assetId, host = host.host, command = command, exitCode = result.exitCode)
        // 总耗时（建连 + 执行），由 execute 统一计时。
        return result.copy(durationMs = System.currentTimeMillis() - t0)
    }

    /** 自带的 exec：复用 SshClients.execChannel(:135) 的逻辑，额外收 stderr。耗时由调用方 [execute] 统一计。 */
    private fun exec(
        session: ClientSession,
        command: String,
        hostName: String,
    ): ExecResult {
        val bout = ByteArrayOutputStream()
        val berr = ByteArrayOutputStream()
        val channel = session.createExecChannel(command)
        channel.out = bout
        channel.err = berr
        if (channel.open().verify(timeout).await(timeout)) {
            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), timeout)
        }
        // exitCode = -1 表示未能取得远端退出状态（如通道异常关闭/未回 exit-status），
        // 并非命令真实返回 -1；调用方据此判断成败时需注意。
        val exit = channel.exitStatus ?: -1
        IOUtils.closeQuietly(channel)
        return ExecResult(
            exitCode = exit,
            stdout = bout.toString(Charsets.UTF_8),
            stderr = berr.toString(Charsets.UTF_8),
            host = hostName,
        )
    }
}
