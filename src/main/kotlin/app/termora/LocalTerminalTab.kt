package app.termora

import app.termora.terminal.PtyConnector
import org.apache.commons.io.Charsets
import java.nio.charset.StandardCharsets

class LocalTerminalTab(host: Host) : PtyHostTerminalTab(host) {
    init {
        terminal.getTerminalModel().setData(TerminalTab, this)
    }

    override suspend fun openPtyConnector(): PtyConnector {
        val winSize = terminalPanel.winSize()
        val ptyConnector = PtyConnectorFactory.instance.createPtyConnector(
            winSize.rows, winSize.cols,
            host.options.envs(),
            Charsets.toCharset(host.options.encoding, StandardCharsets.UTF_8),
        )

        return ptyConnector
    }

}