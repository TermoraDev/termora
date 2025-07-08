package app.termora

import app.termora.Application.ohMyJson
import app.termora.database.Data
import app.termora.database.DataType
import app.termora.database.DatabaseChangedExtension
import app.termora.database.DatabaseManager


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

}