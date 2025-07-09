package app.termora.plugin.internal.telnet

import app.termora.terminal.StreamPtyConnector
import org.apache.commons.io.IOUtils
import org.apache.commons.net.telnet.TelnetClient
import org.apache.commons.net.telnet.TelnetOption
import org.apache.commons.net.telnet.WindowSizeOptionHandler
import java.io.InputStreamReader
import java.nio.charset.Charset

class TelnetStreamPtyConnector(
    private val telnet: TelnetClient,
    private val charset: Charset,
    private val characterMode: Boolean,
) : StreamPtyConnector(telnet.inputStream, telnet.outputStream) {

    private val reader = InputStreamReader(telnet.inputStream, charset)


    override fun read(buffer: CharArray): Int {
        return reader.read(buffer)
    }

    override fun write(buffer: ByteArray, offset: Int, len: Int) {
        if (characterMode) {
            for (i in offset until len + offset) {
                output.write(byteArrayOf(buffer[i]))
                output.flush()
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
        telnet.disconnect()
    }

    override fun getCharset(): Charset {
        return charset
    }

}