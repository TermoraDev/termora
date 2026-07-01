package app.termora.cli.output

import app.termora.Application.ohMyJson
import app.termora.cli.AssetInfo
import app.termora.cli.AssetList
import app.termora.cli.ExecResult
import app.termora.cli.TransferResult

/**
 * @param json true 输出 JSON（被管道/agent 调用时）；false 输出人类可读（交互终端）。
 * 生产构造默认 `json = System.console() == null`。
 */
class CliOutput(private val json: Boolean) {

    fun renderExec(result: ExecResult): String {
        if (json) return ohMyJson.encodeToString(ExecResult.serializer(), result)
        return buildString {
            if (result.stdout.isNotEmpty()) append(result.stdout)
            if (result.stderr.isNotEmpty()) {
                if (isNotEmpty() && !endsWith("\n")) append('\n')
                append(result.stderr)
            }
        }
    }

    fun renderAssets(assets: List<AssetInfo>): String {
        if (json) return ohMyJson.encodeToString(AssetList.serializer(), AssetList(assets))
        if (assets.isEmpty()) return "（白名单为空，没有可访问的主机）\n"
        return assets.joinToString("\n", postfix = "\n") {
            "${it.id}  ${it.name}  ${it.username}@${it.host}:${it.port}  [${it.protocol}]" +
                if (it.remark.isNotBlank()) "  ${it.remark}" else ""
        }
    }

    fun renderTransfer(result: TransferResult): String {
        if (json) return ohMyJson.encodeToString(TransferResult.serializer(), result)
        return "ok=${result.ok} bytes=${result.bytes} host=${result.host}\n"
    }

    companion object {
        fun auto(): CliOutput = CliOutput(json = System.console() == null)
    }
}
