package app.termora.plugins.sync

import app.termora.ApplicationScope
import app.termora.Disposable
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

@Suppress("DuplicatedCode")
class SyncManager private constructor() : Disposable {
    companion object {
        private val log = LoggerFactory.getLogger(SyncManager::class.java)

        fun getInstance(): SyncManager {
            return ApplicationScope.forApplicationScope().getOrCreate(SyncManager::class) { SyncManager() }
        }
    }

    private val sync get() = SyncProperties.getInstance()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var disableTrigger = false


    private fun trigger() {
        trigger(getSyncConfig())
    }

    fun triggerOnChanged() {
        if (sync.policy == SyncPolicy.OnChange.name) {
            trigger()
        }
    }

    private fun trigger(config: SyncConfig) {
        if (disableTrigger) return

        job?.cancel()

        if (log.isInfoEnabled) {
            log.info("Automatic synchronisation is interrupted")
        }

        job = coroutineScope.launch {

            // 因为会频繁调用，等待 10 - 30 秒
            val seconds = Random.nextInt(10, 30)
            if (log.isInfoEnabled) {
                log.info("Trigger synchronisation, which will take place after {} seconds", seconds)
            }

            delay(seconds.seconds)


            if (!disableTrigger) {
                try {

                    if (log.isInfoEnabled) {
                        log.info("Automatic synchronisation begin")
                    }

                    // 如果已经开始，设置为 null
                    // 因为同步的时候会修改数据，避免被中断
                    job = null

                    sync(config)

                    sync.lastSyncTime = System.currentTimeMillis()

                    if (log.isInfoEnabled) {
                        log.info("Automatic synchronisation end")
                    }

                } catch (e: Exception) {
                    if (log.isErrorEnabled) {
                        log.error(e.message, e)
                    }
                }
            }
        }
    }

    fun sync(config: SyncConfig): SyncResponse {
        return syncImmediately(config)
    }


    private fun getSyncConfig(): SyncConfig {
        val range = mutableSetOf<SyncRange>()
        if (sync.rangeHosts) {
            range.add(SyncRange.Hosts)
        }
        if (sync.rangeKeyPairs) {
            range.add(SyncRange.KeyPairs)
        }
        if (sync.rangeKeywordHighlights) {
            range.add(SyncRange.KeywordHighlights)
        }
        if (sync.rangeMacros) {
            range.add(SyncRange.Macros)
        }
        if (sync.rangeKeymap) {
            range.add(SyncRange.Keymap)
        }
        if (sync.rangeSnippets) {
            range.add(SyncRange.Snippets)
        }
        return SyncConfig(
            type = sync.type,
            token = sync.token,
            gistId = sync.gist,
            options = mapOf("domain" to sync.domain),
            ranges = range
        )
    }


    private fun syncImmediately(config: SyncConfig): SyncResponse {
        synchronized(this) {
            return SyncResponse(pull(config), push(config))
        }
    }


    fun pull(config: SyncConfig): GistResponse {
        synchronized(this) {
            disableTrigger = true
            try {
                return SyncerProvider.getInstance().getSyncer(config.type).pull(config)
            } finally {
                disableTrigger = false
            }
        }
    }

    fun push(config: SyncConfig): GistResponse {
        synchronized(this) {
            try {
                disableTrigger = true
                return SyncerProvider.getInstance().getSyncer(config.type).push(config)
            } finally {
                disableTrigger = false
            }
        }
    }


    override fun dispose() {
        coroutineScope.cancel()
    }


    private class SyncerProvider private constructor() {
        companion object {
            fun getInstance(): SyncerProvider {
                return ApplicationScope.forApplicationScope().getOrCreate(SyncerProvider::class) { SyncerProvider() }
            }
        }


        fun getSyncer(type: SyncType): Syncer {
            return when (type) {
                SyncType.GitHub -> GitHubSyncer.getInstance()
                SyncType.Gitee -> GiteeSyncer.getInstance()
                SyncType.GitLab -> GitLabSyncer.getInstance()
                SyncType.WebDAV -> WebDAVSyncer.getInstance()
            }
        }
    }

    data class SyncResponse(val pull: GistResponse, val push: GistResponse)

}