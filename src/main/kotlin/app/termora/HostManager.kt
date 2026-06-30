package app.termora

import app.termora.Application.ohMyJson
import app.termora.account.AccountManager
import app.termora.database.Data
import app.termora.database.DataType
import app.termora.database.DatabaseChangedExtension
import app.termora.database.DatabaseManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.nio.file.Files


class HostManager private constructor() : Disposable {
    companion object {
        fun getInstance(): HostManager {
            return ApplicationScope.forApplicationScope().getOrCreate(HostManager::class) { HostManager() }
        }
    }

    private val databaseManager get() = DatabaseManager.getInstance()

    /**
     * 修改缓存并存入数据库
     */
    fun addHost(host: Host, source: DatabaseChangedExtension.Source = DatabaseChangedExtension.Source.User) {
        assertEventDispatchThread()

        if (host.isTemporary)
            throw IllegalArgumentException("Temporary host")

        if (host.ownerType.isBlank())
            throw IllegalArgumentException("Owner type cannot be null")

        databaseManager.saveAndIncrementVersion(
            Data(
                id = host.id,
                ownerId = host.ownerId,
                ownerType = host.ownerType,
                type = DataType.Host.name,
                data = ohMyJson.encodeToString(host),
            ),
            source
        )

    }

    fun removeHost(id: String) {
        databaseManager.delete(id, DataType.Host.name)
    }

    /**
     * 第一次调用从数据库中获取，后续从缓存中获取
     */
    fun hosts(): List<Host> {
        return databaseManager.data<Host>(DataType.Host)
            .sortedWith(compareBy<Host> { if (it.isFolder) 0 else 1 }.thenBy { it.sort })
    }

    /**
     * 从缓存中获取
     */
    fun getHost(id: String): Host? {
        val data = databaseManager.data(id) ?: return null
        if (data.type != DataType.Host.name) return null
        if (data.deleted) return null
        return ohMyJson.decodeFromString(data.data)
    }

    /**
     * 导出所有主机配置到JSON文件
     */
    fun exportHosts(file: File) {
        val hosts = hosts()
        val exportData = HostExportData(
            version = 1,
            exportDate = System.currentTimeMillis(),
            hosts = hosts.map { it.copy(deleted = false) } // 导出时不包含deleted标记
        )
        val json = ohMyJson.encodeToString(exportData)
        Files.writeString(file.toPath(), json)
    }

    /**
     * 从JSON文件导入主机配置
     * @param file 要导入的文件
     * @param replaceAll true=替换所有现有主机，false=合并（保留现有主机）
     * @return 导入的主机数量
     */
    fun importHosts(file: File, replaceAll: Boolean): Int {
        assertEventDispatchThread()
        val json = Files.readString(file.toPath())
        val importData = ohMyJson.decodeFromString<HostExportData>(json)

        // 获取当前账户的 ownerId
        // 优先使用账户管理器的ID，如果是本地账户则使用 "0"
        val currentOwnerId = if (AccountManager.getInstance().isLocally()) {
            "0"
        } else {
            AccountManager.getInstance().getAccountId()
        }
        val currentOwnerType = "User"

        if (replaceAll) {
            // 删除所有现有主机
            hosts().forEach { removeHost(it.id) }
        }

        // 导入新主机，更新 ownerId 和 ownerType 为当前用户
        importData.hosts.forEach { host ->
            val updatedHost = host.copy(
                ownerId = currentOwnerId,
                ownerType = currentOwnerType
            )
            addHost(updatedHost, DatabaseChangedExtension.Source.User)
        }

        return importData.hosts.size
    }

    @Serializable
    private data class HostExportData(
        val version: Int,
        val exportDate: Long,
        val hosts: List<Host>
    )

}