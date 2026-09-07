package app.termora.cli

import app.termora.Host
import app.termora.cli.guard.WhitelistManager

/**
 * 列出可暴露给外部 agent 的主机。
 * @param hostsProvider 取全部主机；生产用 HostManager.getInstance()::hosts
 */
class AssetLister(
    private val hostsProvider: () -> List<Host>,
    private val whitelist: WhitelistManager,
) {
    fun list(keyword: String? = null): List<AssetInfo> {
        val kw = keyword?.trim()?.lowercase()
        val allowed = whitelist.ids()
        return hostsProvider()
            .filter { !it.isFolder && !it.deleted }
            .filter { it.id in allowed }
            .filter { kw.isNullOrEmpty() || matches(it, kw) }
            .map {
                AssetInfo(
                    id = it.id, name = it.name, protocol = it.protocol,
                    host = it.host, port = it.port, username = it.username, remark = it.remark,
                )
            }
    }

    private fun matches(host: Host, kwLower: String): Boolean =
        host.name.lowercase().contains(kwLower) ||
            host.host.lowercase().contains(kwLower) ||
            host.remark.lowercase().contains(kwLower)
}
