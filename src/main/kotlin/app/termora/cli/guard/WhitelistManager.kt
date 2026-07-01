package app.termora.cli.guard

import app.termora.Application.ohMyJson

/**
 * 主机白名单（只读）。白名单是 host id 列表，存于数据库 setting `cli.whitelist`（JSON 数组）。
 * @param reader 返回 cli.whitelist 的原始 JSON 字符串
 */
class WhitelistManager(
    private val reader: () -> String,
) {
    fun isAllowed(hostId: String): Boolean = ids().contains(hostId)

    fun ids(): Set<String> {
        val raw = reader().trim()
        if (raw.isEmpty()) return emptySet()
        return runCatching {
            ohMyJson.decodeFromString<List<String>>(raw).toSet()
        }.getOrDefault(emptySet())
    }

    companion object {
        const val SETTING_KEY = "cli.whitelist"

        /** termora-cli 总开关 setting key（默认 false） */
        const val ENABLED_KEY = "cli.enabled"
    }
}
