package app.termora

import app.termora.terminal.PtyConnector
import org.apache.commons.io.Charsets
import java.nio.charset.StandardCharsets
import javax.swing.Icon

class SerialTerminalTab(windowScope: WindowScope, host: Host, terminalTabbedManager: TerminalTabbedManager) :
    PtyHostTerminalTab(windowScope, host, terminalTabbedManager) {
    override suspend fun openPtyConnector(): PtyConnector {
        val serialPort = Serials.openPort(host)
        return SerialPortPtyConnector(
            serialPort,
            Charsets.toCharset(host.options.encoding, StandardCharsets.UTF_8)
        )
    }

    override fun getIcon(): Icon {
        return Icons.plugin
    }
}