package app.termora.plugin.internal.telnet

import app.termora.terminal.StreamPtyConnector
import org.apache.commons.io.IOUtils
import org.apache.commons.net.telnet.TelnetClient
import org.apache.commons.net.telnet.TelnetOption
import org.apache.commons.net.telnet.WindowSizeOptionHandler
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.charset.Charset

class TelnetStreamPtyConnector(
    private val telnet: TelnetClient,
    private val charset: Charset,
    private val characterMode: Boolean,
) : StreamPtyConnector(telnet.inputStream, telnet.outputStream) {
    companion object {
        private val log = LoggerFactory.getLogger(TelnetStreamPtyConnector::class.java)
    }

    private val reader = InputStreamReader(telnet.inputStream, charset)
    private val pos = if (characterMode) PipedOutputStream() else OutputStream.nullOutputStream()
    private val thread = if (characterMode) Thread.ofVirtual().unstarted(CharacterModeRunnable()) else null

    init {
        if (characterMode) {
            thread?.start()
        }
    }

    override fun read(buffer: CharArray): Int {
        if (characterMode) {
            return reader.read(buffer, 0, 1)
        }
        return reader.read(buffer)
    }

    override fun write(buffer: ByteArray, offset: Int, len: Int) {
        if (characterMode) {
            pos.write(buffer, offset, len)
            pos.flush()
        } else {
            output.write(buffer, offset, len)
            output.flush()
        }
    }

    override fun resize(rows: Int, cols: Int) {
        telnet.deleteOptionHandler(TelnetOption.WINDOW_SIZE)
        telnet.addOptionHandler(WindowSizeOptionHandler(cols, rows, true, false, true, false))
    }

    override fun waitFor(): Int {
        return -1
    }

    override fun close() {
        IOUtils.closeQuietly(pos)
        IOUtils.closeQuietly(input)
        IOUtils.closeQuietly(output)
        thread?.interrupt()
        telnet.disconnect()
    }

    override fun getCharset(): Charset {
        return charset
    }

    private inner class CharacterModeRunnable : Runnable {
        private val pis = PipedInputStream(pos as PipedOutputStream)
        private val writer = OutputStreamWriter(output, charset)
        private val reader = InputStreamReader(pis, charset)

        override fun run() {
            try {
                while (Thread.currentThread().isInterrupted.not()) {
                    val c = reader.read()
                    if (c == -1) break
                    writer.write(c)
                    writer.flush()
                }
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            }
        }
    }
}