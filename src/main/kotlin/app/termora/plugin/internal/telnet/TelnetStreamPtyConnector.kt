package app.termora.plugin.internal.telnet

import app.termora.terminal.StreamPtyConnector
import org.apache.commons.io.IOUtils
import org.apache.commons.net.telnet.TelnetClient
import org.apache.commons.net.telnet.TelnetOption
import org.apache.commons.net.telnet.WindowSizeOptionHandler
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.concurrent.LinkedBlockingQueue

class TelnetStreamPtyConnector(
    private val telnet: TelnetClient,
    private val charset: Charset,
    private val characterMode: Boolean,
) : StreamPtyConnector(telnet.inputStream, telnet.outputStream) {

    private val reader = InputStreamReader(telnet.inputStream, charset)
    private val thread = if (characterMode) Thread.ofVirtual().unstarted(CharacterModeRunnable()) else null
    private val queue = LinkedBlockingQueue<ByteArray>()

    init {
        if (characterMode) {
            thread?.start()
        }
    }

    override fun read(buffer: CharArray): Int {
        return reader.read(buffer)
    }

    override fun write(buffer: ByteArray, offset: Int, len: Int) {
        if (characterMode) {
            for (i in offset until len + offset) {
                queue.offer(byteArrayOf(buffer[i]))
            }
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
        IOUtils.closeQuietly(input)
        IOUtils.closeQuietly(output)
        thread?.interrupt()
        telnet.disconnect()
    }

    override fun getCharset(): Charset {
        return charset
    }

    private inner class CharacterModeRunnable : Runnable {

        override fun run() {
            while (Thread.currentThread().isInterrupted.not()) {
                val bytes = queue.take()
                output.write(bytes)
                output.flush()
                Thread.sleep(100)
            }
        }
    }
}