package app.termora.plugins.geo

import app.termora.Application
import app.termora.ApplicationRunnerExtension
import app.termora.randomUUID
import app.termora.swingCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.net.ProxySelector
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class GeoApplicationRunnerExtension private constructor() : ApplicationRunnerExtension {
    companion object {
        private val log = LoggerFactory.getLogger(GeoApplicationRunnerExtension::class.java)
        val instance = GeoApplicationRunnerExtension()
    }

    private var ready = false
    private val httpClient by lazy {
        Application.httpClient.newBuilder()
            .callTimeout(15, TimeUnit.MINUTES)
            .readTimeout(10, TimeUnit.MINUTES)
            .proxySelector(ProxySelector.getDefault())
            .build()
    }

    override fun ready() {

        val databaseFile = Geo.getInstance().getDatabaseFile()
        if (databaseFile.exists()) {
            ready = true
            return
        }

        // 重新加载
        reload()

    }

    fun isReady() = ready

    internal fun reload() {
        ready = false

        val databaseFile = Geo.getInstance().getDatabaseFile()

        swingCoroutineScope.launch(Dispatchers.IO) {
            var timeout = 3

            while (ready.not()) {
                try {
                    FileUtils.forceMkdirParent(databaseFile)

                    downloadGeoLite2(databaseFile)

                    withContext(Dispatchers.Swing) { GeoHostTreeShowMoreEnableExtension.instance.updateComponentTreeUI() }
                } catch (e: Exception) {
                    if (log.isWarnEnabled) {
                        log.warn(e.message, e)
                    }
                }
                delay(timeout.seconds)
                timeout = timeout * 2
            }
        }
    }


    private fun downloadGeoLite2(dbFile: File) {
        val url = "https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-Country.mmdb"
        val response = httpClient.newCall(
            Request.Builder().get().url(url)
                .build()
        ).execute()
        log.info("Fetched GeoLite2-Country.mmdb from {} status {}", url, response.code)
        if (response.isSuccessful.not()) {
            IOUtils.closeQuietly(response)
            throw IllegalStateException("GeoLite2-Country.mmdb could not be downloaded, HTTP ${response.code}")
        }

        val body = response.body
        val input = body?.byteStream()
        val file = FileUtils.getFile(Application.getTemporaryDir(), randomUUID())
        val output = file.outputStream()

        val downloaded = runCatching { IOUtils.copy(input, output) }.isSuccess
        IOUtils.closeQuietly(input, output, body, response)

        log.info("Downloaded GeoLite2-Country.mmdb from {} , result: {}", url, downloaded)

        if (downloaded) {
            FileUtils.moveFile(file, dbFile)
            ready = true
        } else {
            throw IllegalStateException("GeoLite2-Country.mmdb could not be downloaded")
        }

    }
}