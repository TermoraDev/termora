package app.termora

import com.formdev.flatlaf.util.SystemInfo
import com.github.hstyi.restart4j.Restarter
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.awt.Component
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.jvm.optionals.getOrNull

class TermoraRestarter {
    companion object {
        private val log = LoggerFactory.getLogger(TermoraRestarter::class.java)

        fun getInstance(): TermoraRestarter {
            return ApplicationScope.forApplicationScope().getOrCreate(TermoraRestarter::class) { TermoraRestarter() }
        }

        init {
            Restarter.setProcessHandler { ProcessHandle.current().pid().toInt() }
        }

    }

    private val restarting = AtomicBoolean(false)
    private val isSupported get() = !restarting.get() && checkIsSupported()
    private val isLinuxAppImage by lazy { System.getenv("LinuxAppImage")?.toBoolean() == true }
    private val startupCommand by lazy { ProcessHandle.current().info().command().getOrNull() }
    private val macOSApplicationPath by lazy {
        StringUtils.removeEndIgnoreCase(
            Application.getAppPath(),
            "/Contents/MacOS/Termora"
        )
    }

    private fun restart() {
        if (!isSupported) return
        if (!restarting.compareAndSet(false, true)) return

        SwingUtilities.invokeLater {
            try {
                doRestart()
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            }
        }
    }

    /**
     * 计划重启，如果当前进程支持重启，那么会询问用户是否重启。如果不支持重启，那么弹窗提示需要手动重启。
     */
    fun scheduleRestart(owner: Component) {

        if (isSupported) {
            if (OptionPane.showConfirmDialog(
                    owner,
                    I18n.getString("termora.settings.restart.message"),
                    I18n.getString("termora.settings.restart.title"),
                    messageType = JOptionPane.QUESTION_MESSAGE,
                    optionType = JOptionPane.YES_NO_OPTION,
                    options = arrayOf(
                        I18n.getString("termora.settings.restart.title"),
                        I18n.getString("termora.cancel")
                    ),
                    initialValue = I18n.getString("termora.settings.restart.title")
                ) == JOptionPane.YES_OPTION
            ) {
                restart()
            }
        } else {
            OptionPane.showMessageDialog(
                owner,
                I18n.getString("termora.settings.restart.message"),
                I18n.getString("termora.settings.restart.title"),
                messageType = JOptionPane.INFORMATION_MESSAGE,
            )
        }

    }

    private fun doRestart() {

        if (SystemInfo.isMacOS) {
            Restarter.restart(arrayOf("open", "-n", macOSApplicationPath))
        } else if (SystemInfo.isWindows && startupCommand != null) {
            Restarter.restart(arrayOf(startupCommand))
        } else if (SystemInfo.isLinux) {
            if (isLinuxAppImage) {
                Restarter.restart(arrayOf(System.getenv("APPIMAGE")))
            } else if (startupCommand != null) {
                Restarter.restart(arrayOf(startupCommand))
            }
        }

        for (window in TermoraFrameManager.getInstance().getWindows()) {
            window.dispose()
        }
    }


    private fun checkIsSupported(): Boolean {
        val appPath = Application.getAppPath()
        if (appPath.isBlank() || Application.isUnknownVersion()) {
            if (log.isWarnEnabled) {
                log.warn("Restart not supported")
            }
            return false
        }

        log.info("startupCommand: ${startupCommand}")
        log.info("apppath: ${Application.getAppPath()}")

        if (SystemInfo.isWindows && startupCommand == null) {
            if (log.isWarnEnabled) {
                log.warn("Restart not supported , ProcessHandle#info#command is null.")
            }
            return false
        }

        if (SystemInfo.isLinux) {
            if (isLinuxAppImage) {
                val appImage = System.getenv("APPIMAGE") ?: StringUtils.EMPTY
                return appImage.isNotBlank() && FileUtils.getFile(appImage).exists()
            }
            return startupCommand != null
        }

        if (SystemInfo.isMacOS) {
            return Application.getAppPath().isNotBlank()
        }


        return true
    }


}