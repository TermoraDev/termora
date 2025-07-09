package app.termora.plugin.internal.update

import app.termora.*
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import com.formdev.flatlaf.util.SystemInfo
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinReg
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.JXEditorPane
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.net.URI
import javax.swing.BorderFactory
import javax.swing.JOptionPane
import javax.swing.JScrollPane
import javax.swing.UIManager
import javax.swing.event.HyperlinkEvent

internal class AppUpdateAction private constructor() : AnAction(StringUtils.EMPTY, Icons.ideUpdate) {

    companion object {
        private val log = LoggerFactory.getLogger(AppUpdateAction::class.java)

        fun getInstance(): AppUpdateAction {
            return ApplicationScope.forApplicationScope().getOrCreate(AppUpdateAction::class) { AppUpdateAction() }
        }
    }

    private val updaterManager get() = UpdaterManager.getInstance()

    init {
        isEnabled = false
    }


    override fun actionPerformed(evt: AnActionEvent) {
        showUpdateDialog()
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
            optionType = JOptionPane.OK_CANCEL_OPTION,
            options = arrayOf(
                I18n.getString("termora.update.update"),
                I18n.getString("termora.cancel")
            ),
            initialValue = I18n.getString("termora.update.update")
        )
        if (option == JOptionPane.CANCEL_OPTION) {
            return
        } else if (option == JOptionPane.OK_OPTION) {
            updateSelf(lastVersion)
        }
    }

    private fun updateSelf(latestVersion: UpdaterManager.LatestVersion) {
        val pkg = Updater.getInstance().getLatestPkg()
        if (SystemInfo.isLinux || pkg == null) {
            Application.browse(URI.create("https://github.com/TermoraDev/termora/releases/tag/${latestVersion.version}"))
            return
        }
        val file = pkg.file
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