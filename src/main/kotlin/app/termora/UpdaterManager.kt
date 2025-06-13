package app.termora

import app.termora.Application.ohMyJson
import kotlinx.serialization.json.*
import okhttp3.Request
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.time.DateFormatUtils
import org.commonmark.node.BulletList
import org.commonmark.node.Heading
import org.commonmark.node.Paragraph
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.renderer.html.HtmlRenderer
import org.semver4j.Semver
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*


class UpdaterManager private constructor() {
    companion object {
        private val log = LoggerFactory.getLogger(UpdaterManager::class.java)
        fun getInstance(): UpdaterManager {
            return ApplicationScope.forApplicationScope().getOrCreate(UpdaterManager::class) { UpdaterManager() }
        }
    }

    data class Asset(
        val name: String,
        val url: String,
        val downloadUrl: String,
        val size: Long
    )

    data class LatestVersion(
        // tag name
        val version: String,
        val prerelease: Boolean,
        val draft: Boolean,
        val name: String,
        val createdDate: Date,
        val publishedDate: Date,
        val body: String,
        val htmlBody: String,
        val assets: List<Asset>
    ) {
        companion object {
            val self = LatestVersion(
                version = Application.getVersion(),
                prerelease = false,
                draft = false,
                name = StringUtils.EMPTY,
                createdDate = Date(),
                publishedDate = Date(),
                body = StringUtils.EMPTY,
                htmlBody = StringUtils.EMPTY,
                assets = emptyList()
            )
        }

        val isSelf get() = this == self
    }

    var lastVersion = LatestVersion.self

    fun fetchLatestVersion(): LatestVersion {
        try {
            val isBetaVersion = Application.isBetaVersion()
            val url = StringBuilder("https://api.github.com/repos/TermoraDev/termora/releases")
            if (isBetaVersion) {
                url.append("?per_page=10")
            } else {
                url.append("/latest")
            }

            val request = Request.Builder().get()
                .url(url.toString())
                .build()
            val response = Application.httpClient.newCall(request).execute()
            if (response.isSuccessful.not()) {
                if (log.isErrorEnabled) {
                    log.error("Failed to fetch latest version, response was ${response.code}")
                }
                IOUtils.closeQuietly(response)
                return LatestVersion.self
            }

            val text = response.use { resp -> resp.body?.use { it.string() } }
            if (text.isNullOrBlank()) {
                return LatestVersion.self
            }

            val json = if (isBetaVersion) getLatestBetaRelease(text) else ohMyJson.parseToJsonElement(text).jsonObject
            if (json == null) return LatestVersion.self

            val version = json.getValue("tag_name").jsonPrimitive.content
            val prerelease = json.getValue("prerelease").jsonPrimitive.boolean
            val draft = json.getValue("draft").jsonPrimitive.boolean
            val name = json.getValue("name").jsonPrimitive.content
            val createdDate = Date.from(Instant.parse(json.getValue("created_at").jsonPrimitive.content))
            val publishedDate =
                Date.from(Instant.parse(json.getValue("published_at").jsonPrimitive.content))
            val body = json.getValue("body").jsonPrimitive.content
            val assets = json.getValue("assets").jsonArray.map { it.jsonObject }
                .map {
                    Asset(
                        name = it.getValue("name").jsonPrimitive.content,
                        url = it.getValue("url").jsonPrimitive.content,
                        downloadUrl = it.getValue("browser_download_url").jsonPrimitive.content,
                        size = it.getValue("size").jsonPrimitive.long,
                    )
                }

            val parser = Parser.builder().build()
            val document = parser.parse(
                "# 🎉 ${name.trim()} (${
                    DateFormatUtils.format(
                        publishedDate,
                        "yyyy-MM-dd"
                    )
                }) \n${body.trim()}"
            )
            val renderer = HtmlRenderer.builder()
                .attributeProviderFactory {
                    AttributeProvider { node, _, attributes ->
                        if (attributes != null) {
                            if (node is Heading) {
                                attributes["style"] = "margin: 5px 0;"
                            } else if (node is BulletList) {
                                attributes["style"] = "margin: 0 20px;"
                            } else if (node is Paragraph) {
                                attributes["style"] = "margin: 0;"
                            }
                        }
                    }
                }
                .build()

            lastVersion = LatestVersion(
                version = version,
                prerelease = prerelease,
                draft = draft,
                name = name,
                body = body,
                htmlBody = renderer.render(document),
                createdDate = createdDate,
                publishedDate = publishedDate,
                assets = assets
            )

            return lastVersion
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error("Failed to get latest version", e)
            }
        }
        return LatestVersion.self
    }

    private fun getLatestBetaRelease(text: String): JsonObject? {
        val releases = parseReleases(text)
        if (releases.isEmpty()) return null
        return releases.maxByOrNull { it.first }?.second
    }

    private fun parseReleases(text: String): List<Pair<Semver, JsonObject>> {
        val array = ohMyJson.parseToJsonElement(text).jsonArray
        val releases = mutableListOf<Pair<Semver, JsonObject>>()
        for (e in array) {
            val version = e.jsonObject.getValue("tag_name").jsonPrimitive.content
            val prerelease = e.jsonObject.getValue("prerelease").jsonPrimitive.boolean
            if (prerelease.not()) continue
            val semver = Semver.parse(version) ?: continue
            releases.add(semver to e.jsonObject)
        }
        return releases
    }
}