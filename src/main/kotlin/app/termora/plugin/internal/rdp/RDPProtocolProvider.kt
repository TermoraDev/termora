package app.termora.plugin.internal.rdp

import app.termora.*
import app.termora.actions.DataProvider
import app.termora.protocol.GenericProtocolProvider
import com.formdev.flatlaf.util.SystemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.net.URI
import javax.swing.JOptionPane
import kotlin.time.Duration.Companion.seconds

internal class RDPProtocolProvider private constructor() : GenericProtocolProvider {
    companion object {
        val instance by lazy { RDPProtocolProvider() }
        const val PROTOCOL = "RDP"
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun createTerminalTab(dataProvider: DataProvider, windowScope: WindowScope, host: Host): TerminalTab {
        TODO()
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon {
        return Icons.microsoftWindows
    }

    override fun canCreateTerminalTab(dataProvider: DataProvider, windowScope: WindowScope, host: Host): Boolean {
        openRDP(windowScope, host)
        return false
    }

    private fun openRDP(windowScope: WindowScope, host: Host) {
        if (SystemInfo.isLinux) {
            OptionPane.showMessageDialog(
                windowScope.window,
                "Linux cannot connect to Windows Remote Server, Supported only for macOS and Windows",
                messageType = JOptionPane.WARNING_MESSAGE
            )
            return
        }

        if (SystemInfo.isMacOS) {
            if (!FileUtils.getFile("/Applications/Windows App.app").exists()) {
                val option = OptionPane.showConfirmDialog(
                    windowScope.window,
                    "If you want to connect to a Windows Remote Server, You have to install the Windows App",
                    optionType = JOptionPane.OK_CANCEL_OPTION
                )
                if (option == JOptionPane.OK_OPTION) {
                    Application.browse(URI.create("https://apps.apple.com/app/windows-app/id1295203466"))
                }
                return
            }
        }

        val sb = StringBuilder()
        sb.append("full address:s:").append(host.host).append(':').append(host.port).appendLine()
        sb.append("username:s:").append(host.username).appendLine()
        val desktop = host.options.extras["desktop"]
        if (desktop.isNullOrBlank().not()) {
            val resolution = desktop.split("x")
            if (resolution.size == 2) {
                sb.append("use multimon:i:0").appendLine()
                sb.append("screen mode id:i:1").appendLine()
                sb.append("desktopwidth:i:").append(resolution.first()).appendLine()
                sb.append("desktopheight:i:").append(resolution.last()).appendLine()

                // macOS 上需要关闭动态分辨率，否则分辨率可能不生效
                if (SystemInfo.isMacOS) {
                    sb.append("dynamic resolution:i:0").appendLine()
                }
            }
        }

        val file = FileUtils.getFile(Application.getTemporaryDir(), randomUUID() + ".rdp")
        file.outputStream().use { IOUtils.write(sb.toString(), it, Charsets.UTF_8) }

        if (host.authentication.type == AuthenticationType.Password) {
            val systemClipboard = windowScope.window.toolkit.systemClipboard
            val password = host.authentication.password
            systemClipboard.setContents(StringSelection(password), null)
            // clear password
            swingCoroutineScope.launch(Dispatchers.IO) {
                delay(30.seconds)
                if (systemClipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    if (systemClipboard.getData(DataFlavor.stringFlavor) == password) {
                        systemClipboard.setContents(StringSelection(StringUtils.EMPTY), null)
                    }
                }
            }
        }

        if (SystemInfo.isMacOS) {
            ProcessBuilder("open", file.absolutePath).start()
        } else if (SystemInfo.isWindows) {
            ProcessBuilder("mstsc", file.absolutePath).start()
        }

    }
}