package app.termora.account

import app.termora.*
import app.termora.Application.ohMyJson
import app.termora.database.Data
import app.termora.database.DatabaseChangedExtension
import app.termora.plugin.DispatchThread
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 同步服务
 */
@Suppress("LoggingSimilarMessage", "DuplicatedCode")
class PushService private constructor() : SyncService(), Disposable, ApplicationRunnerExtension,
    DatabaseChangedExtension {

    companion object {
        private val log = LoggerFactory.getLogger(PushService::class.java)

        fun getInstance(): PushService {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(PushService::class) { PushService() }
        }
    }


    init {
        Disposer.register(this, DynamicExtensionHandler.getInstance().register(AccountExtension::class.java, object :
            AccountExtension {
            override fun onAccountChanged(oldAccount: Account, newAccount: Account) {
                if (oldAccount.isLocally && newAccount.isLocally.not()) {
                    trigger()
                }
            }
        }))
    }

    /**
     * 多次通知只会生效一次 也就是最后一次
     */
    private val channel = Channel<Unit>(Channel.CONFLATED)


    private suspend fun schedule() {
        try {
            if (channel.receiveCatching().isSuccess) {
                synchronize()
            }
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
        }
    }

    private fun synchronize() {
        // 免费方案没有同步
        if (isFreePlan) return

        // 同步
        val list = getUnsyncedData()
        for (data in list) {
            try {
                synchronize(data)
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            }
        }

    }


    private fun getUnsyncedData(): List<Data> {
        val ownerIds = accountManager.getOwnerIds()
        return databaseManager.unsyncedData().filter { ownerIds.contains(it.ownerId) }
            .filterNot { AccountManager.isLocally(it.ownerId) }
    }


    private fun synchronize(data: Data) {
        syncLock.withLock { if (data.deleted) delete(data) else push(data) }
    }

    private fun delete(data: Data) {
        val request = Request.Builder()
            .url("${accountManager.getServer()}/v1/data/${data.id}?ownerId=${data.ownerId}&ownerType=${data.ownerType}")
            .delete()
            .build()

        AccountHttp.execute(request = request)

        // 修改为已经同步
        updateData(data.id, synced = true)

        if (log.isInfoEnabled) {
            log.info("数据: {} 已从云端删除", data.id)
        }
    }

    private fun push(data: Data) {
        val requestData = PushDataRequest(
            objectId = data.id,
            ownerId = data.ownerId,
            ownerType = data.ownerType,
            version = data.version,
            type = data.type,
            data = encryptData(data.id, data.data),
        )

        val request = Request.Builder().url("${accountManager.getServer()}/v1/data/push")
            .post(ohMyJson.encodeToString(requestData).toRequestBody("application/json".toMediaType()))
            .build()
        val response = AccountHttp.client.newCall(request).execute()

        val text = response.use { response.body.use { it?.string() } }
        if (text.isNullOrBlank()) {
            throw ResponseException(response.code, "response body is empty", response)
        }

        // 如果是 403 ，说明没有权限
        if (response.code == 403) {
            if (log.isWarnEnabled) {
                log.warn("数据: {} 没有权限推送到云端，此数据将在下次修改时推送", data.id)
            }
            // 标记为已经同步
            updateData(data.id, synced = true, version = data.version)
            return
        } else if (response.code == 409) { // 版本冲突，一般来说是云端版本大于本地版本
            val json = ohMyJson.decodeFromString<JsonObject>(text)
            // 最新版
            val version = json["data"]?.jsonObject?.get("version")?.jsonPrimitive?.long
            if (version == null) {
                if (log.isWarnEnabled) {
                    log.warn("数据: {} 推送时版本冲突，触发拉取", data.id)
                }
            }
            PullService.getInstance().trigger()
            return
        }

        // 没有错误就是推送成功了
        if (response.isSuccessful.not()) {
            throw ResponseException(response.code, response)
        }

        // 获取到响应体
        val json = ohMyJson.decodeFromString<JsonObject>(text)
        val version = json["version"]?.jsonPrimitive?.long ?: data.version

        // 修改为已经同步，并且将版本改为云端版本
        updateData(data.id, synced = true, version = version)

        if (log.isInfoEnabled) {
            log.info("数据: {} 已推送至云端", data.id)
        }

    }

    override fun ready() {

        // 同步
        swingCoroutineScope.launch(Dispatchers.IO) { while (isActive) schedule() }

        // 定时同步
        swingCoroutineScope.launch(Dispatchers.IO) {
            delay(10.seconds)
            while (isActive) {
                // 发送同步
                channel.send(Unit)
                // 每 1 分钟尝试同步一次，除非收到数据变动通知
                delay(1.minutes)
            }
        }

        Disposer.register(this, object : Disposable {
            override fun dispose() {
                channel.close()
            }
        })

    }

    override fun onDataChanged(
        id: String,
        type: String,
        action: DatabaseChangedExtension.Action,
        source: DatabaseChangedExtension.Source
    ) {
        if (source == DatabaseChangedExtension.Source.User) trigger()
    }

    override fun getDispatchThread(): DispatchThread {
        return DispatchThread.BGT
    }

    fun trigger() {
        channel.trySend(Unit).isSuccess
    }


    @Serializable
    private data class PushDataRequest(
        val objectId: String,
        val ownerId: String,
        val ownerType: String,
        val version: Long,
        val type: String,
        val data: String
    )
}