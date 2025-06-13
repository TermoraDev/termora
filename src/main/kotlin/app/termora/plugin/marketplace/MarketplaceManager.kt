package app.termora.plugin.marketplace

import app.termora.*
import app.termora.plugin.PluginDescription
import app.termora.plugin.internal.plugin.PluginSVGIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okio.withLock
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.dom4j.io.SAXReader
import org.semver4j.Semver
import org.slf4j.LoggerFactory
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import javax.swing.Icon
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

internal class MarketplaceManager private constructor() {
    companion object {
        private val log = LoggerFactory.getLogger(MarketplaceManager::class.java)
        fun getInstance(): MarketplaceManager {
            return ApplicationScope.Companion.forApplicationScope()
                .getOrCreate(MarketplaceManager::class) { MarketplaceManager() }
        }
    }

    private val isLoading = AtomicBoolean(false)
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private val plugins = mutableListOf<MarketplacePlugin>()
    private val exceptionReference = AtomicReference<Exception>(null)

    fun getPlugins(): List<MarketplacePlugin> {
        if (plugins.isNotEmpty()) return plugins

        refreshPlugins()

        return plugins
    }

    fun clear() {
        plugins.clear()
    }

    private fun refreshPlugins() {
        if (isLoading.compareAndSet(false, true)) {
            try {
                exceptionReference.set(null)
                val pls = doGetPlugins()
                plugins.clear()
                plugins.addAll(pls)
            } catch (e: Exception) {
                exceptionReference.set(e)
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            } finally {
                lock.withLock {
                    isLoading.set(false)
                    condition.signalAll()
                }
            }
        } else {
            lock.lock()
            try {
                condition.await()
            } finally {
                lock.unlock()
            }
        }

        val exception = exceptionReference.get()
        if (exception != null) {
            throw exception
        }
    }

    private fun doGetPlugins(): List<MarketplacePlugin> {
        val version = Semver.parse(Application.getVersion())
            ?: return emptyList()
        val language = I18n.containsLanguage(Locale.getDefault()) ?: "en_US"
        val request = Request.Builder()
            .get()
            .header("X-Version", version.toString())
            .header("X-Language", language)
            .header("X-OS", SystemUtils.OS_NAME)
            .header("X-Arch", SystemUtils.OS_ARCH)
            .url("https://github.com/TermoraDev/termora-marketplace/releases/latest/download/plugins.xml")
            .build()
        val response = Application.httpClient.newCall(request).execute()
        if (response.isSuccessful.not()) {
            IOUtils.closeQuietly(response)
            throw ResponseException(response.code, response)
        }

        val text = response.use { res -> res.body?.use { it.string() } }
        if (text.isNullOrBlank()) {
            throw ResponseException(response.code, response)
        }

        val root = SAXReader().read(InputSource(StringReader(text))).rootElement
        val plugins = mutableListOf<MarketplacePlugin>()
        for (pluginElement in root.elements("plugin")) {
            val id = pluginElement.element("id")?.textTrim ?: continue
            val name = pluginElement.element("name")?.textTrim ?: continue
            val paid = pluginElement.element("paid") != null
            val icon = pluginElement.element("icon")?.textTrim ?: StringUtils.EMPTY
            val darkIcon = pluginElement.element("dark-icon")?.textTrim ?: StringUtils.EMPTY

            val descriptions = mutableListOf<PluginDescription>()
            val versions = mutableListOf<MarketplacePluginVersion>()
            var vendor = PluginVendor(StringUtils.EMPTY, StringUtils.EMPTY)

            for (versionElement in pluginElement.element("versions")?.elements("version") ?: emptyList()) {
                val version = Semver.parse(versionElement.element("version")?.textTrim ?: continue) ?: continue
                val since = versionElement.element("since")?.textTrim ?: continue
                val until = versionElement.element("until")?.textTrim ?: StringUtils.EMPTY
                val downloadUrl = versionElement.element("download-url")?.textTrim ?: continue
                val signature = versionElement.element("signature")?.textTrim ?: continue
                versions.add(
                    MarketplacePluginVersion(
                        version = version,
                        since = since,
                        until = until,
                        downloadUrl = downloadUrl,
                        signature = signature,
                    )
                )
            }

            // 根据版本排序
            versions.sortByDescending { it.version }

            for (elementDescription in pluginElement.element("descriptions")?.elements("description") ?: emptyList()) {
                descriptions.add(
                    PluginDescription(
                        elementDescription.attributeValue("language") ?: StringUtils.EMPTY,
                        elementDescription.textTrim
                    )
                )
            }

            val vendorElement = pluginElement.element("vendor")
            if (vendorElement != null) {
                vendor = vendor.copy(name = vendorElement.textTrim)
                val url = vendorElement.attributeValue("url")
                if (url.isNullOrBlank().not()) {
                    vendor = vendor.copy(url = url)
                }
            }


            var myIcon: Icon = Icons.plugin
            if (icon.isNotBlank() && darkIcon.isNotBlank()) {
                myIcon = PluginSVGIcon(icon.toByteArray().inputStream(), darkIcon.toByteArray().inputStream())
            } else if (icon.isNotBlank()) {
                myIcon = PluginSVGIcon(icon.toByteArray().inputStream())
            }

            plugins.add(
                MarketplacePlugin(
                    id = id,
                    name = name,
                    icon = myIcon,
                    paid = paid,
                    versions = versions,
                    descriptions = descriptions,
                    vendor = vendor
                )
            )
        }

        if (plugins.isEmpty()) {
            return emptyList()
        }

        val matchedPlugins = mutableListOf<MarketplacePlugin>()

        // 获取到合适的插件
        for (e in plugins) {
            for (plugin in e.versions) {
                if (version.satisfies(plugin.since).not()) continue
                if (plugin.until.isNotBlank())
                    if (version.satisfies(plugin.until).not()) continue
                matchedPlugins.add(e.copy(versions = mutableListOf(plugin)))
                break
            }
        }

        return matchedPlugins.sortedBy { it.name.length }
    }


    class MarketplaceManagerReady private constructor() : ApplicationRunnerExtension {
        companion object {
            val instance = MarketplaceManagerReady()
        }

        override fun ready() {
            swingCoroutineScope.launch(Dispatchers.IO) {
                // load plugins
                delay(1.seconds)
                while (isActive) {
                    runCatching { getInstance().refreshPlugins() }
                    // 每小时刷新一下
                    delay(1.hours)
                }
            }
        }
    }
}