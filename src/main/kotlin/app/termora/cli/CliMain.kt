package app.termora.cli

import app.termora.HostManager
import app.termora.cli.guard.Auditor
import app.termora.cli.guard.DangerousCommandFilter
import app.termora.cli.guard.WhitelistManager
import app.termora.cli.output.CliOutput
import app.termora.database.DatabaseManager
import kotlin.system.exitProcess

private const val USAGE = """termora-cli — 通过 Termora 已保存的主机配置执行远程命令

用法:
  termora-cli exec <asset-id> "<command>"     执行远程命令
  termora-cli exec <asset-id> --stdin          从标准输入读取命令（规避 shell 转义）
  termora-cli assets list [-q <keyword>] [--json]   列出白名单内可访问的主机（不含凭证）
  termora-cli upload <asset-id> <local> <remote>    上传本地文件到远程（SFTP，scp 语义）
  termora-cli download <asset-id> <remote> <local>  从远程下载文件到本地（SFTP，scp 语义）
  termora-cli --help
"""

fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] == "--help" || args[0] == "-h") {
        println(USAGE)
        exitProcess(if (args.isEmpty()) ExitCodes.USAGE else ExitCodes.OK)
    }

    try {
        when (args[0]) {
            "exec" -> {
                CliBootstrap.init(); ensureEnabled()
                runExec(args.drop(1))
            }
            "assets" -> {
                CliBootstrap.init(); ensureEnabled()
                runAssets(args.drop(1))
            }
            "upload" -> {
                CliBootstrap.init(); ensureEnabled()
                runUpload(args.drop(1))
            }
            "download" -> {
                CliBootstrap.init(); ensureEnabled()
                runDownload(args.drop(1))
            }
            else -> throw CliError(ExitCodes.USAGE, "未知子命令: ${args[0]}\n$USAGE")
        }
    } catch (e: CliError) {
        System.err.println(e.message)
        exitProcess(e.code)
    }
}

private fun runExec(rest: List<String>) {
    if (rest.isEmpty()) throw CliError(ExitCodes.USAGE, "缺少 <asset-id>\n$USAGE")
    val assetId = rest[0]
    val command = when {
        rest.getOrNull(1) == "--stdin" -> System.`in`.readBytes().toString(Charsets.UTF_8)
        rest.size >= 2 -> rest.drop(1).joinToString(" ")
        else -> throw CliError(ExitCodes.USAGE, "缺少命令\n$USAGE")
    }

    val executor = SshExecutor(
        hostResolver = HostManager.getInstance()::getHost,
        whitelist = whitelistManager(),
        dangerous = DangerousCommandFilter(),
        auditor = Auditor(Auditor.defaultFile()),
    )

    val result = executor.execute(assetId, command)
    val out = CliOutput.auto()
    print(out.renderExec(result))
    // 人类可读模式下，若 stdout 未以换行结尾则补一个换行；JSON 模式不额外加换行。
    if (System.console() != null && !result.stdout.endsWith("\n")) println()
    exitProcess(result.exitCode)
}

/** 从数据库 setting `cli.whitelist` 读取白名单（exec/assets/upload/download 共用）。 */
private fun whitelistManager() = WhitelistManager {
    DatabaseManager.getInstance().properties.getString(WhitelistManager.SETTING_KEY, "[]")
}

/**
 * 校验 termora-cli 总开关（setting `cli.enabled`，默认 false）。
 * 未启用时抛 [CliError]（退出码 6）。须在 [CliBootstrap.init] 之后调用（依赖数据库已初始化）。
 */
private fun ensureEnabled() {
    val enabled = DatabaseManager.getInstance().properties
        .getString(WhitelistManager.ENABLED_KEY, "false")
        .toBooleanStrictOrNull() ?: false
    if (!enabled) {
        throw CliError(
            ExitCodes.DISABLED,
            "termora-cli 未启用。请在 Termora 设置 →「命令行工具」中开启「启用 termora-cli」后再使用。",
        )
    }
}

private fun runAssets(rest: List<String>) {
    if (rest.firstOrNull() != "list") {
        throw CliError(ExitCodes.USAGE, "用法: termora-cli assets list [-q <keyword>] [--json]")
    }
    var keyword: String? = null
    val args = rest.drop(1)
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "-q" -> {
                keyword = args.getOrNull(i + 1)
                i += 2
            }
            // 其它参数（含显式 --json）一律忽略：输出模式由 CliOutput.auto() 按 TTY 决定
            else -> i += 1
        }
    }
    val lister = AssetLister(
        hostsProvider = HostManager.getInstance()::hosts,
        whitelist = whitelistManager(),
    )
    print(CliOutput.auto().renderAssets(lister.list(keyword)))
    exitProcess(ExitCodes.OK)
}

private fun newSftpTransfer() = SftpTransfer(
    hostResolver = HostManager.getInstance()::getHost,
    whitelist = whitelistManager(),
    auditor = Auditor(Auditor.defaultFile()),
)

private fun runUpload(rest: List<String>) {
    if (rest.size < 3) throw CliError(ExitCodes.USAGE, "用法: termora-cli upload <asset-id> <local> <remote>")
    val r = newSftpTransfer().upload(rest[0], rest[1], rest[2])
    print(CliOutput.auto().renderTransfer(r))
    exitProcess(if (r.ok) ExitCodes.OK else ExitCodes.CONNECT_FAIL)
}

private fun runDownload(rest: List<String>) {
    if (rest.size < 3) throw CliError(ExitCodes.USAGE, "用法: termora-cli download <asset-id> <remote> <local>")
    val r = newSftpTransfer().download(rest[0], rest[1], rest[2])
    print(CliOutput.auto().renderTransfer(r))
    exitProcess(if (r.ok) ExitCodes.OK else ExitCodes.CONNECT_FAIL)
}
