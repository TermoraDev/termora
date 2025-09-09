package app.termora.plugin.internal.wsl

import app.termora.*
import app.termora.terminal.PtyConnector
import org.apache.commons.io.Charsets
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

class WSLHostTerminalTab(windowScope: WindowScope, host: Host) : PtyHostTerminalTab(windowScope, host) {
    companion object {
        fun parseCommand(command: String): List<String> {
            val result = mutableListOf<String>()
            val matcher = Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(command)

            while (matcher.find()) {
                if (matcher.group(1) != null) {
                    result.add(matcher.group(1)) // 处理双引号部分
                } else {
                    result.add(matcher.group(2).replace("\\\\ ", " "))
                }
            }
            return result
        }
    }

    override suspend fun openPtyConnector(): PtyConnector {
        val winSize = terminalPanel.winSize()
        val drive = System.getenv("SystemRoot")
        val wsl = FileUtils.getFile(drive, "System32", "wsl.exe").absolutePath
        val commands = mutableListOf<String>()
        commands.add(wsl)
        commands.add("-d")
        commands.add(host.host)

        if (StringUtils.isNotBlank(host.username)) {
            commands.add("-u")
            commands.add(host.username)
        }

        commands.add("--cd")
        commands.add("~")


        if (StringUtils.isNoneBlank(host.options.startupCommand)) {
            commands.addAll(parseCommand(host.options.startupCommand))
        }

        val ptyConnector = PtyConnectorFactory.getInstance().createPtyConnector(
            commands = commands.toTypedArray(),
            rows = winSize.rows, cols = winSize.cols,
            env = host.options.envs(),
            charset = Charsets.toCharset(host.options.encoding, StandardCharsets.UTF_8),
        )

        return ptyConnector
    }

    override fun createReconnectTerminalTab(): TerminalTab {
        return WSLHostTerminalTab(windowScope, host)
    }


    override fun sendStartupCommand(ptyConnector: PtyConnector, bytes: ByteArray) {
        // Nothing
    }
}