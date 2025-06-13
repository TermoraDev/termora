package app.termora.plugins.sync

import app.termora.Application.ohMyJson
import app.termora.ApplicationScope
import app.termora.ResponseException
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.apache.commons.lang3.StringUtils

class GiteeSyncer private constructor() : GitSyncer() {

    companion object {
        fun getInstance(): GiteeSyncer {
            return ApplicationScope.forApplicationScope().getOrCreate(GiteeSyncer::class) { GiteeSyncer() }
        }
    }

    override fun newPullRequestBuilder(config: SyncConfig): Request.Builder {
        val gistId = StringUtils.defaultIfBlank(config.gistId, "empty")

        return Request.Builder().get()
            .url("https://gitee.com/api/v5/gists/${gistId}?access_token=${config.token}")
    }

    override fun newPushRequestBuilder(gistFiles: List<GistFile>, config: SyncConfig): Request.Builder {
        val content = ohMyJson.encodeToString(buildJsonObject {
            if (config.gistId.isBlank()) {
                put("public", false)
            }
            put("description", description)
            put("access_token", config.token)
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
        if (config.gistId.isBlank()) {
            builder.post(content.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .url("https://gitee.com/api/v5/gists")
        } else {
            builder.patch(content.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .url("https://gitee.com/api/v5/gists/${config.gistId}")
        }

        return builder
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
                    filename = key,
                    content = file.getValue("content").jsonPrimitive.content,
                )
            )
        }

        return gistResponse.copy(gists = gists)
    }


}