package app.termora

import app.termora.localshell.LocalShellProvider
import app.termora.terminal.PtyConnector
import org.apache.commons.io.Charsets
import java.nio.charset.StandardCharsets

class LocalTerminalTab(host: Host) : PtyHostTerminalTab(host) {

    override suspend fun openPtyConnector(): PtyConnector {
        val winSize = terminalPanel.winSize()

        val localShellProvider = LocalShellProvider.makeProviderByIdentificationString(host.options.localShell) ?: LocalShellProvider.FollowGlobal
        val localShell = localShellProvider.localShell ?: throw IllegalStateException("Local shell not found")
        val ptyConnector =
            PtyConnectorFactory.instance.createPtyConnector(
                localShell,
                winSize.rows, winSize.cols,
                host.options.envs(),
                Charsets.toCharset(host.options.encoding, StandardCharsets.UTF_8),
            )
        return ptyConnector
    }

}