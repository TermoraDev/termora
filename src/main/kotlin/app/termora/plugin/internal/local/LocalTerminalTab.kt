package app.termora.plugin.internal.local

import app.termora.*
import app.termora.database.DatabaseManager
import app.termora.terminal.PtyConnector
import app.termora.terminal.PtyConnectorDelegate
import app.termora.terminal.PtyProcessConnector
import org.apache.commons.io.Charsets
import org.apache.commons.lang3.SystemUtils
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import javax.swing.Icon
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.jvm.optionals.getOrNull

class LocalTerminalTab(windowScope: WindowScope, host: Host) :
    PtyHostTerminalTab(windowScope, host) {

    companion object {
        private val log = LoggerFactory.getLogger(LocalTerminalTab::class.java)
    }

    override suspend fun openPtyConnector(): PtyConnector {
        val winSize = terminalPanel.winSize()
        val workDir = host.options.extras["workDir"]

        val ptyConnector = if (workDir != null && workDir.isNotBlank()) {
            val command = DatabaseManager.getInstance().terminal.localShell
            val commands = mutableListOf(command)
            if (SystemUtils.IS_OS_UNIX) {
                commands.add("-l")
            }
            PtyConnectorFactory.Companion.getInstance().createPtyConnector(
                commands.toTypedArray(),
                winSize.rows, winSize.cols,
                host.options.envs(),
                workDir,
                Charsets.toCharset(host.options.encoding, StandardCharsets.UTF_8),
            )
        } else {
            PtyConnectorFactory.Companion.getInstance().createPtyConnector(
                winSize.rows, winSize.cols,
                host.options.envs(),
                Charsets.toCharset(host.options.encoding, StandardCharsets.UTF_8),
            )
        }

        return ptyConnector
    }

    override fun getIcon(): Icon {
        return Icons.terminal
    }

    override fun willBeClose(): Boolean {
        val ptyProcessConnector = getPtyProcessConnector() ?: return true
        val process = ptyProcessConnector.process
        var consoleProcessCount = 0

        try {
            val processHandle = ProcessHandle.of(process.pid()).getOrNull()
            if (processHandle != null) {
                consoleProcessCount = processHandle.children().count().toInt()
            }
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
        }

        // 没有正在运行的进程
        if (consoleProcessCount < 1) return true

        val owner = SwingUtilities.getWindowAncestor(terminalPanel) ?: return true
        return OptionPane.showConfirmDialog(
            owner,
            I18n.getString("termora.tabbed.local-tab.close-prompt"),
            messageType = JOptionPane.INFORMATION_MESSAGE,
            optionType = JOptionPane.OK_CANCEL_OPTION
        ) == JOptionPane.OK_OPTION
    }

    override fun createReconnectTerminalTab(): TerminalTab {
        return LocalTerminalTab(windowScope, host)
    }

    private fun getPtyProcessConnector(): PtyProcessConnector? {
        var p = getPtyConnector() as PtyConnector?
        while (p != null) {
            if (p is PtyProcessConnector) return p
            if (p is PtyConnectorDelegate) p = p.ptyConnector
        }
        return null
    }
}