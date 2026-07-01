package app.termora.cli

import app.termora.Host
import app.termora.cli.guard.Auditor
import app.termora.cli.guard.WhitelistManager
import app.termora.plugin.internal.ssh.SshClients
import org.apache.commons.io.IOUtils
import org.apache.sshd.sftp.client.impl.DefaultSftpClientFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * SFTP 文件传输：白名单校验 → openClient/openSession → SFTP → Files.copy（scp 语义）→ 审计。
 * 单文件传输；文件夹递归属后续增强。失败统一转 CliError。
 *
 * @param hostResolver 生产用 HostManager.getInstance()::getHost
 */
class SftpTransfer(
    private val hostResolver: (String) -> Host?,
    private val whitelist: WhitelistManager,
    private val auditor: Auditor,
) {
    fun upload(assetId: String, localPath: String, remotePath: String): TransferResult =
        run(assetId, "upload", localPath, remotePath) { fs, host ->
            val src = Path.of(localPath)
            val dst = resolveScpTarget(fs.getPath(remotePath), src.fileName.toString())
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
            TransferResult(ok = true, bytes = Files.size(dst), host = host.name)
        }

    fun download(assetId: String, remotePath: String, localPath: String): TransferResult =
        run(assetId, "download", remotePath, localPath) { fs, host ->
            val src = fs.getPath(remotePath)
            val dst = resolveScpTarget(Path.of(localPath), src.fileName.toString())
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
            TransferResult(ok = true, bytes = Files.size(dst), host = host.name)
        }

    /** scp 语义：目标已存在且是目录 → 源保留原名放入；否则目标路径即最终路径。 */
    private fun resolveScpTarget(target: Path, sourceName: String): Path =
        if (Files.isDirectory(target)) target.resolve(sourceName) else target

    private fun run(
        assetId: String, action: String, from: String, to: String,
        block: (java.nio.file.FileSystem, Host) -> TransferResult,
    ): TransferResult {
        val host = hostResolver(assetId) ?: throw CliError(ExitCodes.NOT_FOUND, "主机不存在: $assetId")
        if (!whitelist.isAllowed(assetId)) {
            auditor.record(
                assetId = assetId, host = host.host, action = action,
                command = "$action $from -> $to", exitCode = ExitCodes.FORBIDDEN,
            )
            throw CliError(ExitCodes.FORBIDDEN, "主机未授权 AI 访问（不在白名单）: ${host.name}")
        }
        try {
            val client = SshClients.openClient(host)
            try {
                val session = SshClients.openSession(host, client)
                val fs = DefaultSftpClientFactory.INSTANCE.createSftpFileSystem(session)
                val result = fs.use { block(it, host) }
                auditor.record(
                    assetId = assetId, host = host.host, action = action,
                    command = "$action $from -> $to", exitCode = 0,
                )
                return result
            } finally {
                IOUtils.closeQuietly(client)
            }
        } catch (e: CliError) {
            throw e
        } catch (e: Exception) {
            auditor.record(
                assetId = assetId, host = host.host, action = action,
                command = "$action $from -> $to", exitCode = ExitCodes.CONNECT_FAIL,
            )
            throw CliError(ExitCodes.CONNECT_FAIL, "$action 失败: ${e.message}", e)
        }
    }
}
