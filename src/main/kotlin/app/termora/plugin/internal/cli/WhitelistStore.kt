package app.termora.plugin.internal.cli

import app.termora.Application.ohMyJson
import app.termora.cli.guard.WhitelistManager

/**
 * termora-cli 主机白名单的读写封装。白名单是 host id 列表，存数据库 setting `cli.whitelist`（JSON 数组）。
 * 与 CLI 侧 [WhitelistManager] 读取的是同一 key，保证 GUI 写入 = CLI 读取。
 * 读取直接复用 [WhitelistManager]，避免 JSON 解析逻辑重复。
 *
 * @param read  返回 cli.whitelist 原始 JSON；生产用 DatabaseManager.properties.getString(KEY,"[]")
 * @param write 写入 cli.whitelist 原始 JSON；生产用 DatabaseManager.properties.putString(KEY, _)
 */
class WhitelistStore(
    read: () -> String,
    private val write: (String) -> Unit,
) {
    private val manager = WhitelistManager(read)

    fun ids(): Set<String> = manager.ids()

    fun isEnabled(id: String): Boolean = manager.isAllowed(id)

    fun setEnabled(id: String, enabled: Boolean) {
        val current = manager.ids().toMutableSet()
        if (enabled) current.add(id) else current.remove(id)
        write(ohMyJson.encodeToString(current.toList()))
    }

    companion object {
        /** 复用 CLI 侧的 key，避免读写不一致 */
        val SETTING_KEY: String get() = WhitelistManager.SETTING_KEY
    }
}
