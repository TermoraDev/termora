package app.termora.plugins.sync

import app.termora.Application.ohMyJson
import app.termora.DeletedData
import app.termora.ResponseException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.ArrayUtils
import org.slf4j.LoggerFactory

abstract class GitSyncer : SafetySyncer() {
    companion object {
        private val log = LoggerFactory.getLogger(GitSyncer::class.java)
    }

    override fun pull(config: SyncConfig): GistResponse {

        if (log.isInfoEnabled) {
            log.info("Type: ${config.type} , Gist: ${config.gistId} Pull...")
        }

        val response = httpClient.newCall(newPullRequestBuilder(config).build()).execute()
        if (response.isSuccessful.not()) {
            IOUtils.closeQuietly(response)
            throw ResponseException(response.code, response)
        }

        val gistResponse = parsePullResponse(response, config)
        val deletedData = mutableListOf<DeletedData>()

        // DeletedData
        gistResponse.gists.findLast { it.filename == "DeletedData" }
            ?.let { deletedData.addAll(decodeDeletedData(it.content, config)) }

        // decode hosts
        if (config.ranges.contains(SyncRange.Hosts)) {
            gistResponse.gists.findLast { it.filename == "Hosts" }?.let {
                decodeHosts(it.content, deletedData.filter { e -> e.type == "Host" }, config)
            }
        }

        // decode keys
        if (config.ranges.contains(SyncRange.KeyPairs)) {
            gistResponse.gists.findLast { it.filename == "KeyPairs" }?.let {
                decodeKeys(it.content, deletedData.filter { e -> e.type == "KeyPair" }, config)
            }
        }

        // decode keyword highlights
        if (config.ranges.contains(SyncRange.KeywordHighlights)) {
            gistResponse.gists.findLast { it.filename == "KeywordHighlights" }?.let {
                decodeKeywordHighlights(it.content, deletedData.filter { e -> e.type == "KeywordHighlight" }, config)
            }
        }

        // decode macros
        if (config.ranges.contains(SyncRange.Macros)) {
            gistResponse.gists.findLast { it.filename == "Macros" }?.let {
                decodeMacros(it.content, deletedData.filter { e -> e.type == "Macro" }, config)
            }
        }

        // decode keymaps
        if (config.ranges.contains(SyncRange.Macros)) {
            gistResponse.gists.findLast { it.filename == "Keymaps" }?.let {
                decodeKeymaps(it.content, deletedData.filter { e -> e.type == "Keymap" }, config)
            }
        }

        // decode Snippets
        if (config.ranges.contains(SyncRange.Snippets)) {
            gistResponse.gists.findLast { it.filename == "Snippets" }?.let {
                decodeSnippets(it.content, deletedData.filter { e -> e.type == "Snippet" }, config)
            }
        }


        if (log.isInfoEnabled) {
            log.info("Type: ${config.type} , Gist: ${config.gistId} Pulled")
        }

        return gistResponse
    }


    override fun push(config: SyncConfig): GistResponse {

        if (log.isInfoEnabled) {
            log.info("Type: ${config.type} , Gist: ${config.gistId} Push...")
        }

        val gistFiles = mutableListOf<GistFile>()
        // aes key
        val key = ArrayUtils.subarray(config.token.padEnd(16, '0').toByteArray(), 0, 16)

        // Hosts
        if (config.ranges.contains(SyncRange.Hosts)) {
            val hostsContent = encodeHosts(key)
            if (log.isDebugEnabled) {
                log.debug("Push encryptedHosts: {}", hostsContent)
            }
            gistFiles.add(GistFile("Hosts", hostsContent))
        }


        // Snippets
        if (config.ranges.contains(SyncRange.Snippets)) {
            val snippetsContent = encodeSnippets(key)
            if (log.isDebugEnabled) {
                log.debug("Push encryptedSnippets: {}", snippetsContent)
            }
            gistFiles.add(GistFile("Snippets", snippetsContent))
        }

        // KeyPairs
        if (config.ranges.contains(SyncRange.KeyPairs)) {
            val keysContent = encodeKeys(key)
            if (log.isDebugEnabled) {
                log.debug("Push encryptedKeys: {}", keysContent)
            }
            gistFiles.add(GistFile("KeyPairs", keysContent))
        }

        // Highlights
        if (config.ranges.contains(SyncRange.KeywordHighlights)) {
            val keywordHighlightsContent = encodeKeywordHighlights(key)
            if (log.isDebugEnabled) {
                log.debug("Push keywordHighlights: {}", keywordHighlightsContent)
            }
            gistFiles.add(GistFile("KeywordHighlights", keywordHighlightsContent))
        }

        // Macros
        if (config.ranges.contains(SyncRange.Macros)) {
            val macrosContent = encodeMacros(key)
            if (log.isDebugEnabled) {
                log.debug("Push macros: {}", macrosContent)
            }
            gistFiles.add(GistFile("Macros", macrosContent))
        }

        // Keymap
        if (config.ranges.contains(SyncRange.Keymap)) {
            val keymapsContent = encodeKeymaps()
            if (log.isDebugEnabled) {
                log.debug("Push keymaps: {}", keymapsContent)
            }
            gistFiles.add(GistFile("Keymaps", keymapsContent))
        }

        if (gistFiles.isEmpty()) {
            throw IllegalArgumentException("No gist files found")
        }

        val deletedData = encodeDeletedData(config)
        if (log.isDebugEnabled) {
            log.debug("Push DeletedData: {}", deletedData)
        }
        gistFiles.add(GistFile("DeletedData", deletedData))

        val request = newPushRequestBuilder(gistFiles, config).build()

        try {
            return parsePushResponse(httpClient.newCall(request).execute(), config)
        } finally {
            if (log.isInfoEnabled) {
                log.info("Type: ${config.type} , Gist: ${config.gistId} Pushed")
            }
        }
    }

    open fun parsePullResponse(response: Response, config: SyncConfig): GistResponse {
        return GistResponse(config, emptyList())
    }

    open fun parsePushResponse(response: Response, config: SyncConfig): GistResponse {
        if (!response.isSuccessful) {
            throw ResponseException(response.code, response)
        }

        val gistResponse = GistResponse(config, emptyList())
        val text = parseResponse(response)
        val json = ohMyJson.parseToJsonElement(text).jsonObject

        return gistResponse.copy(
            config = config.copy(gistId = json.getValue("id").jsonPrimitive.content)
        )
    }

    open fun parseResponse(response: Response): String {
        return response.use { resp -> resp.body?.use { it.string() } }
            ?: throw ResponseException(response.code, response)
    }

    abstract fun newPullRequestBuilder(config: SyncConfig): Request.Builder

    abstract fun newPushRequestBuilder(gistFiles: List<GistFile>, config: SyncConfig): Request.Builder
}