package app.termora.tlog

import app.termora.Host
import app.termora.Icons
import app.termora.Protocol
import app.termora.PtyHostTerminalTab
import app.termora.terminal.PtyConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import javax.swing.Icon

class LogViewerTerminalTab(private val file: File) : PtyHostTerminalTab(
    Host(
        name = file.name,
        protocol = Protocol.Local
    ),
    LogViewerTerminal()
) {

    init {
        // 不记录日志
        terminal.getTerminalModel().setData(TerminalLoggerDataListener.IgnoreTerminalLogger, true)
    }

    override suspend fun openPtyConnector(): PtyConnector {
        if (!file.exists()) {
            throw FileNotFoundException(file.absolutePath)
        }

        val input = withContext(Dispatchers.IO) {
            Files.newBufferedReader(file.toPath())
        }

        return object : PtyConnector {

            override fun read(buffer: CharArray): Int {
                return input.read(buffer)
            }

            override fun write(buffer: ByteArray, offset: Int, len: Int) {

            }

            override fun resize(rows: Int, cols: Int) {

            }

            override fun waitFor(): Int {
                return -1
            }

            override fun close() {
                input.close()
            }

        }
    }

    override fun getIcon(): Icon {
        return Icons.listFiles
    }

    override fun canReconnect(): Boolean {
        return false
    }

    override fun canClone(): Boolean {
        return false
    }
}