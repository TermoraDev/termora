package app.termora.plugins.sync

import app.termora.Application.ohMyJson
import app.termora.ApplicationScope
import app.termora.ResponseException
import kotlinx.serialization.json.*
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class GitHubSyncer private constructor() : GitSyncer() {

    companion object {
        fun getInstance(): GitHubSyncer {
            return ApplicationScope.forApplicationScope().getOrCreate(GitHubSyncer::class) { GitHubSyncer() }
        }
    }

    override fun newPullRequestBuilder(config: SyncConfig): Request.Builder {
        return Request.Builder()
            .get()
            .url("https://api.github.com/gists/${config.gistId}")
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer ${config.token}")
            .header("X-GitHub-Api-Version", "2022-11-28")
    }

    override fun newPushRequestBuilder(gistFiles: List<GistFile>, config: SyncConfig): Request.Builder {
        val create = config.gistId.isBlank()
        val content = ohMyJson.encodeToString(buildJsonObject {
            if (create) {
                put("public", false)
            }
            put("description", description)
            putJsonObject("files") {
                for (file in gistFiles) {
                    putJsonObject(file.filename) {
                        put("content", file.content)
                        put("type", "application/json")
                    }
                }
            }
        })

        val builder = Request.Builder()
        if (create) {
            builder.post(content.toRequestBody())
                .url("https://api.github.com/gists")
        } else {
            builder.patch(content.toRequestBody())
                .url("https://api.github.com/gists/${config.gistId}")
        }

        return builder.header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer ${config.token}")
            .header("X-GitHub-Api-Version", "2022-11-28")
    }

    override fun parsePullResponse(response: Response, config: SyncConfig): GistResponse {
        if (response.code != 200) {
            throw ResponseException(response.code, response)
        }

        val gistResponse = super.parsePullResponse(response, config)
        val text = parseResponse(response)
        val json = ohMyJson.parseToJsonElement(text).jsonObject
        val files = json.getValue("files").jsonObject
        if (files.isEmpty()) {
            return gistResponse
        }

        val gists = mutableListOf<GistFile>()
        for (key in files.keys) {
            val file = files.getValue(key).jsonObject
            gists.add(
                GistFile(
                    filename = file.getValue("filename").jsonPrimitive.content,
                    content = file.getValue("content").jsonPrimitive.content,
                )
            )
        }

        return gistResponse.copy(gists = gists)
    }

}