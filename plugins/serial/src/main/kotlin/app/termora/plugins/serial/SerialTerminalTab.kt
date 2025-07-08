package app.termora.plugins.serial

import app.termora.Host
import app.termora.Icons
import app.termora.PtyHostTerminalTab
import app.termora.WindowScope
import app.termora.terminal.PtyConnector
import org.apache.commons.io.Charsets
import java.nio.charset.StandardCharsets
import javax.swing.Icon

class SerialTerminalTab(windowScope: WindowScope, host: Host) :
    PtyHostTerminalTab(windowScope, host) {
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