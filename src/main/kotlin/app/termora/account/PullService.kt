package app.termora.account

import app.termora.*
import app.termora.Application.ohMyJson
import app.termora.database.Data
import app.termora.database.DatabaseChangedExtension
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.Request
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.withLock
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 同步服务
 */
@Suppress("LoggingSimilarMessage", "DuplicatedCode")
class PullService private constructor() : SyncService(), Disposable, ApplicationRunnerExtension {

    companion object {
        private val log = LoggerFactory.getLogger(PullService::class.java)
        fun getInstance(): PullService {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(PullService::class) { PullService() }
        }
    }

    init {
        Disposer.register(this, DynamicExtensionHandler.getInstance().register(AccountExtension::class.java, object :
            AccountExtension {
            override fun onAccountChanged(oldAccount: Account, newAccount: Account) {
                // 账号变更后重制hash
                if (oldAccount.id != newAccount.id && newAccount.isLocally.not()) {
                    lastChangeHash = StringUtils.EMPTY
                }

                if (oldAccount.isLocally && newAccount.isLocally.not()) {
                    trigger()
                }
            }
        }))
    }

    private val channel = Channel<Unit>(Channel.CONFLATED)
    private val accountProperties get() = AccountProperties.getInstance()
    private val pulling = AtomicBoolean(false)
    private var lastChangeHash = StringUtils.EMPTY

    private fun pullChanges() {
        if (isFreePlan) return
        val hash: String

        try {
            hash = getChangeHash()
            if (hash.isBlank()) return

            if (hash == lastChangeHash) {
                if (log.isDebugEnabled) {
                    log.debug("没有数据变动")
                }
                return
            }

        } catch (e: Exception) {
            if (log.isDebugEnabled) {
                log.debug(e.message, e)
            }
            return
        }

        if (pulling.compareAndSet(false, true).not()) {
            return
        }

        var count = 0
        try {
            PullServiceExtension.firePullStarted()
            count = doPullChanges()
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
        }

        if (hash.isNotBlank()) {
            lastChangeHash = hash
        }

        pulling.set(false)

        PullServiceExtension.firePullFinished(count)
    }

    private fun doPullChanges(): Int {

        if (log.isDebugEnabled) {
            log.debug("即将从云端拉取变更")
        }

        val since = accountProperties.nextSynchronizationSince
        var after = StringUtils.EMPTY
        var nextSince = since
        val limit = 100
        var count = 0

        while (true) {
            val request = Request.Builder()
                .get()
                .url("${accountManager.getServer()}/v1/data/changes?since=${since}&after=${after}&limit=${limit}")
                .build()
            val text = AccountHttp.execute(request = request)
            val response = ohMyJson.decodeFromString<DataChangesResponse>(text)
            if (response.changes.isEmpty()) break

            for (e in response.changes) {
                if (tryPull(e)) {
                    count++
                }
            }

            after = response.after
            nextSince = response.since
            if (response.changes.size < limit) break
        }

        accountProperties.nextSynchronizationSince = nextSince
        accountProperties.lastSynchronizationOn = System.currentTimeMillis()

        if (log.isDebugEnabled) {
            log.debug("从云端拉取变更结束，变更条数: {}", count)
        }

        return count

    }

    private fun tryPull(e: DataChange): Boolean {
        val data = getData(e.objectId)
        // 如果本地不存在，并且云端已经删除，那么不需要处理
        if (data == null && e.deleted) {
            return false
        }

        // 如果云端与本地都已经删除，那么不需要处理
        if (data != null && e.deleted && data.deleted) {
            return false
        }

        if (data == null || data.version != e.version || e.deleted != data.deleted) {
            if (log.isDebugEnabled) {
                log.debug(
                    "数据: {}, 本地版本: {}, 云端版本: {} 触发同步",
                    e.objectId,
                    data?.version ?: "不存在",
                    e.version
                )
            }

            try {
                if (pull(e.objectId, e.ownerId, e.ownerType) == PullResult.Changed) {
                    return true
                }
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            }

        } else if (log.isDebugEnabled) {
            log.debug("数据: {} 本地版本与云端版本一致", e.objectId)
        }

        return false
    }

    private fun getChangeHash(): String {
        val request = Request.Builder()
            .head()
            .url("${accountManager.getServer()}/v1/data/changes")
            .build()
        val response = AccountHttp.client.newCall(request).execute().apply { close() }
        if (response.isSuccessful.not()) {
            IOUtils.closeQuietly(response)
            throw ResponseException(response.code, response)
        }
        return response.header("X-Hash") ?: StringUtils.EMPTY
    }


    private fun pull(id: String, ownerId: String, ownerType: String): PullResult {
        return syncLock.withLock { doPull(id, ownerId, ownerType) }
    }

    private fun doPull(id: String, ownerId: String, ownerType: String): PullResult {
        val request = Request.Builder()
            .url("${accountManager.getServer()}/v1/data/${id}?ownerId=${ownerId}&ownerType=${ownerType}")
            .get()
            .build()
        val response = AccountHttp.client.newCall(request).execute()

        if (log.isDebugEnabled) {
            log.debug("拉取数据: {} 成功, 响应码: {}", id, response.code)
        }

        if(response.isSuccessful.not()){
            IOUtils.closeQuietly(response)
        }

        // 云端数据不存在直接返回
        if (response.code == 404) {
            if (log.isWarnEnabled) {
                log.warn("数据: {} 云端不存在，本地数据将会删除", id)
            }
            // 云端数据不存在，那么本地也要删除
            updateData(id, synced = true, deleted = true)
            return PullResult.Changed
        }

        // 没有权限拉取
        if (response.code == 403) {
            if (log.isWarnEnabled) {
                log.warn("数据: {} 没有权限拉取，本地数据将会删除", id)
            }
            updateData(id, synced = true, deleted = true)
            return PullResult.Changed
        }

        // 其他错误
        if (response.isSuccessful.not()) {
            throw ResponseException(response.code, response)
        }

        val text = response.use { response.body.use { it?.string() } }
        if (text.isNullOrBlank()) {
            throw ResponseException(response.code, "response body is empty", response)
        }

        val json = ohMyJson.decodeFromString<JsonObject>(text)
        val id = json["id"]?.jsonPrimitive?.content
        val ownerId = json["ownerId"]?.jsonPrimitive?.content
        val ownerType = json["ownerType"]?.jsonPrimitive?.content
        val data = json["data"]?.jsonPrimitive?.content
        val type = json["type"]?.jsonPrimitive?.content
        val version = json["version"]?.jsonPrimitive?.long
        val deleted = json["deleted"]?.jsonPrimitive?.boolean
        if (id == null || ownerId == null || ownerType == null || data == null || version == null || deleted == null || type == null) {
            throw IllegalStateException("Data.id $id data error")
        }

        val row = getData(id)

        // 如果本地不存在，
        if (row == null) {
            // 云端已经删除，那么忽略
            if (deleted) {
                if (log.isDebugEnabled) {
                    log.debug("数据: {}, 类型: {} 云端已经删除，本地也不存在", id, type)
                }
                return PullResult.Nothing
            }

            if (log.isDebugEnabled) {
                log.debug("数据: {}, 类型: {} 从云端拉取成功，即将保存到本地", id, type)
            }

            // 保存到本地
            databaseManager.save(
                Data(
                    id = id,
                    ownerId = ownerId,
                    ownerType = ownerType,
                    type = type,
                    data = decryptData(id, data),
                    version = version,
                    // 因为已经是拉取最新版本了，所以这里无需再同步了
                    synced = true,
                    deleted = false
                ),
                DatabaseChangedExtension.Source.Sync
            )

            if (log.isInfoEnabled) {
                log.info("数据: {}, 类型: {} 已保存至本地", id, type)
            }

        } else if (deleted && row.deleted.not()) { // 如果本地存在，云端已经删除，那么本地删除
            if (log.isDebugEnabled) {
                log.debug("数据: {}, 类型: {} 云端已经删除，本地即将删除", id, type)
            }
            databaseManager.delete(
                id, type,
                DatabaseChangedExtension.Source.Sync
            )

            if (log.isInfoEnabled) {
                log.info("数据: {}, 类型: {} 已从本地删除", id, type)

            }
        } else if (row.version > version) { // 如果本地版本大于云端版本，那么忽略，因为需要推送到云端
            if (log.isWarnEnabled) {
                log.warn(
                    "数据: {}, 类型: {}, 本地版本: {}, 云端版本: {}, 本地版本高于云端版本，即将将本地数据推送至云端",
                    id,
                    type,
                    row.version,
                    version
                )
            }
            // 如果没有同步标识，那么修改为代同步
            if (row.synced.not()) {
                updateData(id, false, row.version)
            }
        } else if (row.version < version) { // 本地版本小于云端版本，那么立即修改

            if (log.isDebugEnabled) {
                log.debug(
                    "数据: {}, 类型: {}, 本地版本: {}, 云端版本: {}, 本地版本小于云端版本，即将保存到本地",
                    id,
                    type,
                    row.version,
                    version
                )
            }

            // 解密数据
            databaseManager.save(
                Data(
                    id = id,
                    ownerId = ownerId,
                    ownerType = ownerType,
                    type = type,
                    data = decryptData(id, data),
                    version = version,
                    // 因为已经是拉取最新版本了，所以这里无需再同步了
                    synced = true,
                    deleted = false
                ),
                DatabaseChangedExtension.Source.Sync
            )

            if (log.isInfoEnabled) {
                log.info("数据: {}, 类型: {} 已更新至最新版", id, type)
            }

        } else {
            return PullResult.Nothing
        }

        return PullResult.Changed
    }

    fun trigger() {
        channel.trySend(Unit).isSuccess
    }


    override fun ready() {
        // 定时同步
        swingCoroutineScope.launch(Dispatchers.IO) {
            // 等一会儿再同步
            delay(Random.nextInt(500, 1500).milliseconds)

            while (isActive) {

                // 拉取变动的
                pullChanges()

                // N 秒拉一次
                val result = withTimeoutOrNull(Random.nextInt(5, 15).seconds) {
                    channel.receiveCatching()
                } ?: continue
                if (result.isFailure) break

            }
        }

        Disposer.register(this, object : Disposable {
            override fun dispose() {
                channel.close()
            }
        })
    }

    private enum class PullResult {
        Nothing,
        Changed
    }

    @Serializable
    private data class DataChangesResponse(
        val after: String,
        val since: Long,
        val changes: List<DataChange>
    )

    @Serializable
    private data class DataChange(
        val objectId: String,
        val version: Long,
        val deleted: Boolean,
        val ownerId: String,
        val ownerType: String,
    )


}