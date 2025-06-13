package app.termora.actions

import app.termora.*
import app.termora.Application.httpClient
import com.formdev.flatlaf.util.SystemInfo
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinReg
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import okhttp3.Request
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.JXEditorPane
import org.semver4j.Semver
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.io.File
import java.net.ProxySelector
import java.net.URI
import java.util.*
import java.util.concurrent.TimeUnit
import javax.swing.BorderFactory
import javax.swing.JOptionPane
import javax.swing.JScrollPane
import javax.swing.UIManager
import javax.swing.event.HyperlinkEvent
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class AppUpdateAction private constructor() : AnAction(
    StringUtils.EMPTY,
    Icons.ideUpdate
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)

    companion object {
        private val log = LoggerFactory.getLogger(AppUpdateAction::class.java)
        private const val PKG_FILE_KEY = "pkgFile"

        fun getInstance(): AppUpdateAction {
            return ApplicationScope.forApplicationScope().getOrCreate(AppUpdateAction::class) { AppUpdateAction() }
        }
    }

    private val updaterManager get() = UpdaterManager.getInstance()
    private var isRemindMeNextTime = false

    init {
        isEnabled = false
        scheduleUpdate()
    }

    override fun actionPerformed(evt: AnActionEvent) {
        showUpdateDialog()
    }


    private fun scheduleUpdate() {
        coroutineScope.launch(Dispatchers.IO) {
            // 启动 3 分钟后才是检查
            if (Application.isUnknownVersion().not()) {
                delay(3.minutes)
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

    private suspend fun checkUpdate() {

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


        withContext(Dispatchers.Swing) { isEnabled = true }

    }


    private suspend fun downloadLatestPkg(latestVersion: UpdaterManager.LatestVersion) {
        if (SystemInfo.isLinux) return

        super.putValue(PKG_FILE_KEY, null)
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
        val input = body?.byteStream()
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

        withContext(Dispatchers.Swing) { setLatestPkgFile(file) }

    }

    private fun setLatestPkgFile(file: File) {
        putValue(PKG_FILE_KEY, file)
    }

    private fun getLatestPkgFile(): File? {
        return getValue(PKG_FILE_KEY) as? File
    }

    private fun showUpdateDialog() {
        val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
        val lastVersion = updaterManager.lastVersion
        val editorPane = JXEditorPane()
        editorPane.contentType = "text/html"
        editorPane.text = lastVersion.htmlBody
        editorPane.isEditable = false
        editorPane.addHyperlinkListener {
            if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                Application.browse(it.url.toURI())
            }
        }
        editorPane.background = DynamicColor("window")
        val scrollPane = JScrollPane(editorPane)
        scrollPane.border = BorderFactory.createEmptyBorder()
        scrollPane.preferredSize = Dimension(
            UIManager.getInt("Dialog.width") - 100,
            UIManager.getInt("Dialog.height") - 100
        )

        val option = OptionPane.showConfirmDialog(
            owner,
            scrollPane,
            title = I18n.getString("termora.update.title"),
            messageType = JOptionPane.PLAIN_MESSAGE,
            optionType = JOptionPane.YES_NO_CANCEL_OPTION,
            options = arrayOf(
                I18n.getString("termora.update.update"),
                I18n.getString("termora.update.ignore"),
                I18n.getString("termora.cancel")
            ),
            initialValue = I18n.getString("termora.update.update")
        )
        if (option == JOptionPane.CANCEL_OPTION) {
            return
        } else if (option == JOptionPane.NO_OPTION) {
            isEnabled = false
            isRemindMeNextTime = true
        } else if (option == JOptionPane.YES_OPTION) {
            updateSelf(lastVersion)
        }
    }

    private fun updateSelf(latestVersion: UpdaterManager.LatestVersion) {
        val file = getLatestPkgFile()
        if (SystemInfo.isLinux || file == null) {
            isEnabled = false
            Application.browse(URI.create("https://github.com/TermoraDev/termora/releases/tag/${latestVersion.version}"))
            return
        }

        val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        val commands = if (SystemInfo.isMacOS) listOf("open", "-n", file.absolutePath)
        // 如果安装过，那么直接静默安装和自动启动
        else if (isAppInstalled()) listOf(
            file.absolutePath,
            "/SILENT",
            "/AUTOSTART",
            "/NORESTART",
            "/FORCECLOSEAPPLICATIONS"
        )
        // 没有安装过 则打开安装向导
        else listOf(file.absolutePath)

        if (log.isInfoEnabled) {
            log.info("restart {}", commands.joinToString(StringUtils.SPACE))
        }

        TermoraRestarter.getInstance().scheduleRestart(owner, true, commands)

    }

    private fun isAppInstalled(): Boolean {
        val keyPath = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\${Application.getName()}_is1"
        val phkKey = WinReg.HKEYByReference()

        // 尝试打开注册表键
        val result = Advapi32.INSTANCE.RegOpenKeyEx(
            WinReg.HKEY_LOCAL_MACHINE,
            keyPath,
            0,
            WinNT.KEY_READ,
            phkKey
        )

        if (result == WinError.ERROR_SUCCESS) {
            // 键存在，关闭句柄
            Advapi32.INSTANCE.RegCloseKey(phkKey.getValue())
            return true
        } else {
            // 键不存在或无权限
            return false
        }
    }
}