package app.termora.plugin.internal.update

import app.termora.*
import app.termora.Application.httpClient
import com.formdev.flatlaf.util.SystemInfo
import kotlinx.coroutines.*
import okhttp3.Request
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.semver4j.Semver
import org.slf4j.LoggerFactory
import java.io.File
import java.net.ProxySelector
import java.util.*
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

internal class Updater private constructor() : Disposable {

    companion object {
        private val log = LoggerFactory.getLogger(Updater::class.java)
        fun getInstance(): Updater {
            return ApplicationScope.forApplicationScope().getOrCreate(Updater::class) { Updater() }
        }
    }

    private val updaterManager get() = UpdaterManager.getInstance()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRemindMeNextTime = false
    private val disabledUpdater get() = Application.getLayout() == AppLayout.Appx

    /**
     * 安装包位置
     */
    private var pkg: LatestPkg? = null

    fun scheduleUpdate() {

        if (disabledUpdater) {
            if (coroutineScope.isActive) {
                coroutineScope.cancel()
            }
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            // 启动 3 分钟后才是检查
            if (Application.isUnknownVersion().not()) {
                delay(3.seconds)
            }

            while (coroutineScope.isActive) {
                // 下次提醒我
                if (isRemindMeNextTime) break

                try {
                    checkUpdate()
                } catch (e: Exception) {
                    if (log.isWarnEnabled) {
                        log.warn(e.message, e)
                    }
                }

                // 之后每 3 小时检查一次
                delay(3.hours.inWholeMilliseconds)

            }
        }
    }

    private fun checkUpdate() {

        // Windows 应用商店
        if (disabledUpdater) return

        val latestVersion = updaterManager.fetchLatestVersion()
        if (latestVersion.isSelf) {
            return
        }

        // 之所以放到后面检查是不是开发版本，是需要发起一次检测请求，以方便调试
        if (Application.isUnknownVersion()) {
            return
        }


        val newVersion = Semver.parse(latestVersion.version) ?: return
        val version = Semver.parse(Application.getVersion()) ?: return
        if (newVersion <= version) {
            return
        }

        try {
            downloadLatestPkg(latestVersion)
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
        }


    }


    private fun downloadLatestPkg(latestVersion: UpdaterManager.LatestVersion) {
        if (SystemInfo.isLinux) return

        setLatestPkg(null)

        val arch = if (SystemInfo.isAARCH64) "aarch64" else "x86-64"
        val osName = if (SystemInfo.isWindows) "windows" else "osx"
        val suffix = if (SystemInfo.isWindows) "exe" else "dmg"
        val filename = "termora-${latestVersion.version}-${osName}-${arch}.${suffix}"
        val asset = latestVersion.assets.find { it.name == filename } ?: return

        val response = httpClient
            .newBuilder()
            .callTimeout(15, TimeUnit.MINUTES)
            .readTimeout(15, TimeUnit.MINUTES)
            .proxySelector(ProxySelector.getDefault())
            .build()
            .newCall(Request.Builder().url(asset.downloadUrl).build())
            .execute()
        if (response.isSuccessful.not()) {
            if (log.isErrorEnabled) {
                log.warn("Failed to download latest version ${latestVersion.version}, response code ${response.code}")
            }
            IOUtils.closeQuietly(response)
            return
        }

        val body = response.body
        val input = body.byteStream()
        val file = FileUtils.getFile(Application.getTemporaryDir(), "${UUID.randomUUID()}-${filename}")
        val output = file.outputStream()

        val downloaded = runCatching { IOUtils.copy(input, output) }.isSuccess
        IOUtils.closeQuietly(input, output, body, response)

        if (!downloaded) {
            if (log.isErrorEnabled) {
                log.error("Failed to download latest version to $filename")
            }
            return
        }

        if (log.isInfoEnabled) {
            log.info("Successfully downloaded latest version to $file")
        }

        setLatestPkg(LatestPkg(latestVersion.version, file))
    }

    private fun setLatestPkg(pkg: LatestPkg?) {
        this.pkg = pkg
        SwingUtilities.invokeLater { AppUpdateAction.getInstance().isEnabled = pkg != null }
    }

    fun getLatestPkg(): LatestPkg? {
        return pkg
    }

    data class LatestPkg(val version: String, val file: File)
}