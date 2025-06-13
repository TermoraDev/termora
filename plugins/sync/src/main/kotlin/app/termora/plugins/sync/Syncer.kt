package app.termora.plugins.sync

interface Syncer {
    fun pull(config: SyncConfig): GistResponse

    fun push(config: SyncConfig): GistResponse
}