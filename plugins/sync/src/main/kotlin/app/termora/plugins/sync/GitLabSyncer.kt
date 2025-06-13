package app.termora.plugins.sync

import app.termora.Application.ohMyJson
import app.termora.ApplicationScope
import app.termora.ResponseException
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.apache.commons.io.IOUtils
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class GitLabSyncer private constructor() : GitSyncer() {

    companion object {
        fun getInstance(): GitLabSyncer {
            return ApplicationScope.forApplicationScope().getOrCreate(GitLabSyncer::class) { GitLabSyncer() }
        }
    }

    private val SyncConfig.domain get() = options.getValue("domain")

    private fun getRawSnippet(config: SyncConfig, filename: String): String {
        val name = URLEncoder.encode(filename, StandardCharsets.UTF_8)
        val request = Request.Builder()
            .get()
            .url("${config.domain}/v4/snippets/${config.gistId}/files/main/${name}/raw")
            .header("PRIVATE-TOKEN", config.token)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful.not()) {
                IOUtils.closeQuietly(response)
                throw ResponseException(response.code, response)
            }
            return parseResponse(response)
        }
    }

    override fun newPullRequestBuilder(config: SyncConfig): Request.Builder {
        return Request.Builder()
            .get()
            .url("${config.domain}/v4/snippets/${config.gistId}")
            .header("PRIVATE-TOKEN", config.token)
    }

    override fun newPushRequestBuilder(gistFiles: List<GistFile>, config: SyncConfig): Request.Builder {
        val create = config.gistId.isBlank()
        val oldFileNames = mutableSetOf<String>()

        if (!create) {
            val response = httpClient.newCall(newPullRequestBuilder(config).build()).execute()
            oldFileNames.addAll(parsePullResponseFileNames(response, config))
        }

        val content = ohMyJson.encodeToString(buildJsonObject {
            if (create) {
                put("visibility", "private")
                put("title", description)
            } else {
                put("id", config.gistId.toInt())
            }
            putJsonArray("files") {
                for (file in gistFiles) {
                    add(buildJsonObject {
                        if (!create) {
                            put("action", if (oldFileNames.contains(file.filename)) "update" else "create")
                        }
                        put("content", file.content)
                        put("file_path", file.filename)
                    })
                }
            }
        })

        val requestBody = content.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val builder = Request.Builder()
        if (create) {
            builder.post(requestBody)
                .url("${config.domain}/v4/snippets")
        } else {
            builder.put(requestBody)
                .url("${config.domain}/v4/snippets/${config.gistId}")
        }

        return builder.header("PRIVATE-TOKEN", config.token)
    }

    override fun parsePullResponse(response: Response, config: SyncConfig): GistResponse {

        val gists = mutableListOf<GistFile>()
        val rangeNames = config.ranges.map { it.name }
        for (e in parsePullResponseFileNames(response, config)) {
            if (rangeNames.contains(e)) {
                gists.add(GistFile(filename = e, content = getRawSnippet(config, e)))
            }
        }

        return super.parsePullResponse(response, config).copy(gists = gists)
    }


    private fun parsePullResponseFileNames(response: Response, config: SyncConfig): List<String> {
        if (!response.isSuccessful) {
            throw ResponseException(response.code, response)
        }

        val text = parseResponse(response)
        val json = ohMyJson.parseToJsonElement(text).jsonObject
        val files = json.getValue("files").jsonArray
        if (files.isEmpty()) {
            return emptyList()
        }

        return files.map { it.jsonObject }.map { it.getValue("path").jsonPrimitive.content }
    }

}