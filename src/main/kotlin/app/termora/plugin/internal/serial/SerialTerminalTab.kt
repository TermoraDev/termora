package app.termora.plugin.internal.serial

import app.termora.*
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