package app.termora.plugins.sync

import app.termora.Application.ohMyJson
import app.termora.ApplicationScope
import app.termora.DeletedData
import app.termora.ResponseException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory

class WebDAVSyncer private constructor() : SafetySyncer() {
    companion object {
        private val log = LoggerFactory.getLogger(WebDAVSyncer::class.java)

        fun getInstance(): WebDAVSyncer {
            return ApplicationScope.forApplicationScope().getOrCreate(WebDAVSyncer::class) { WebDAVSyncer() }
        }
    }


    override fun pull(config: SyncConfig): GistResponse {
        val response = httpClient.newCall(newRequestBuilder(config).get().build()).execute()
        if (response.isSuccessful.not()) {
            IOUtils.closeQuietly(response)
            if (response.code == 404) {
                return GistResponse(config, emptyList())
            }
            throw ResponseException(response.code, response)
        }

        val text = response.use { resp -> resp.body?.use { it.string() } }
            ?: throw ResponseException(response.code, response)

        val json = ohMyJson.decodeFromString<JsonObject>(text)
        val deletedData = mutableListOf<DeletedData>()
        json["DeletedData"]?.jsonPrimitive?.content?.let { deletedData.addAll(decodeDeletedData(it, config)) }

        // decode hosts
        if (config.ranges.contains(SyncRange.Hosts)) {
            json["Hosts"]?.jsonPrimitive?.content?.let {
                decodeHosts(it, deletedData.filter { e -> e.type == "Host" }, config)
            }
        }

        // decode KeyPairs
        if (config.ranges.contains(SyncRange.KeyPairs)) {
            json["KeyPairs"]?.jsonPrimitive?.content?.let {
                decodeKeys(it, deletedData.filter { e -> e.type == "KeyPair" }, config)
            }
        }

        // decode Highlights
        if (config.ranges.contains(SyncRange.KeywordHighlights)) {
            json["KeywordHighlights"]?.jsonPrimitive?.content?.let {
                decodeKeywordHighlights(it, deletedData.filter { e -> e.type == "KeywordHighlight" }, config)
            }
        }

        // decode Macros
        if (config.ranges.contains(SyncRange.Macros)) {
            json["Macros"]?.jsonPrimitive?.content?.let {
                decodeMacros(it, deletedData.filter { e -> e.type == "Macro" }, config)
            }
        }

        // decode Keymaps
        if (config.ranges.contains(SyncRange.Keymap)) {
            json["Keymaps"]?.jsonPrimitive?.content?.let {
                decodeKeymaps(it, deletedData.filter { e -> e.type == "Keymap" }, config)
            }
        }

        // decode Snippets
        if (config.ranges.contains(SyncRange.Snippets)) {
            json["Snippets"]?.jsonPrimitive?.content?.let {
                decodeSnippets(it, deletedData.filter { e -> e.type == "Snippet" }, config)
            }
        }

        return GistResponse(config, emptyList())
    }

    override fun push(config: SyncConfig): GistResponse {
        // aes key
        val key = getKey(config)
        val json = buildJsonObject {
            // Hosts
            if (config.ranges.contains(SyncRange.Hosts)) {
                val hostsContent = encodeHosts(key)
                if (log.isDebugEnabled) {
                    log.debug("Push encryptedHosts: {}", hostsContent)
                }
                put("Hosts", hostsContent)
            }

            // Snippets
            if (config.ranges.contains(SyncRange.Snippets)) {
                val snippetsContent = encodeSnippets(key)
                if (log.isDebugEnabled) {
                    log.debug("Push encryptedSnippets: {}", snippetsContent)
                }
                put("Snippets", snippetsContent)
            }

            // KeyPairs
            if (config.ranges.contains(SyncRange.KeyPairs)) {
                val keysContent = encodeKeys(key)
                if (log.isDebugEnabled) {
                    log.debug("Push encryptedKeys: {}", keysContent)
                }
                put("KeyPairs", keysContent)
            }

            // Highlights
            if (config.ranges.contains(SyncRange.KeywordHighlights)) {
                val keywordHighlightsContent = encodeKeywordHighlights(key)
                if (log.isDebugEnabled) {
                    log.debug("Push keywordHighlights: {}", keywordHighlightsContent)
                }
                put("KeywordHighlights", keywordHighlightsContent)
            }

            // Macros
            if (config.ranges.contains(SyncRange.Macros)) {
                val macrosContent = encodeMacros(key)
                if (log.isDebugEnabled) {
                    log.debug("Push macros: {}", macrosContent)
                }
                put("Macros", macrosContent)
            }

            // Keymap
            if (config.ranges.contains(SyncRange.Keymap)) {
                val keymapsContent = encodeKeymaps()
                if (log.isDebugEnabled) {
                    log.debug("Push keymaps: {}", keymapsContent)
                }
                put("Keymaps", keymapsContent)
            }

            // deletedData
            val deletedData = encodeDeletedData(config)
            if (log.isDebugEnabled) {
                log.debug("Push DeletedData: {}", deletedData)
            }
            put("DeletedData", deletedData)
        }

        val response = httpClient.newCall(
            newRequestBuilder(config).put(
                ohMyJson.encodeToString(json)
                    .toRequestBody("application/json".toMediaType())
            ).build()
        ).execute()

        if (response.isSuccessful.not()) {
            IOUtils.closeQuietly(response)
            throw ResponseException(response.code, response)
        }

        return GistResponse(
            config = config,
            gists = emptyList()
        )
    }


    private fun getWebDavFileUrl(config: SyncConfig): String {
        return config.options["domain"] ?: throw IllegalStateException("domain is not defined")
    }

    override fun getKey(config: SyncConfig): ByteArray {
        return PBKDF2.generateSecret(
            config.gistId.toCharArray(),
            config.token.toByteArray(),
            10000, 128
        )
    }

    private fun newRequestBuilder(config: SyncConfig): Request.Builder {
        return Request.Builder()
            .header("Authorization", Credentials.basic(config.gistId, config.token, Charsets.UTF_8))
            .url(getWebDavFileUrl(config))
    }
}