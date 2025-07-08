package app.termora.plugins.serial

import app.termora.terminal.PtyConnector
import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortDataListener
import com.fazecast.jSerialComm.SerialPortEvent
import java.nio.charset.Charset
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class SerialPortPtyConnector(
    private val serialPort: SerialPort,
    private val charset: Charset = Charsets.UTF_8
) : PtyConnector, SerialPortDataListener {

    private val queue = LinkedBlockingQueue<Char>()

    init {
        serialPort.addDataListener(this)
    }

    override fun read(buffer: CharArray): Int {
        buffer[0] = queue.poll(1, TimeUnit.SECONDS) ?: return 0
        return 1
    }

    override fun write(buffer: ByteArray, offset: Int, len: Int) {
        serialPort.writeBytes(buffer, len, offset)
    }

    override fun resize(rows: Int, cols: Int) {

    }

    override fun waitFor(): Int {
        return 0
    }

    override fun close() {
        queue.clear()
        serialPort.closePort()
    }

    override fun getListeningEvents(): Int {
        return SerialPort.LISTENING_EVENT_DATA_RECEIVED
    }

    override fun serialEvent(event: SerialPortEvent) {
        if (event.eventType == SerialPort.LISTENING_EVENT_DATA_RECEIVED) {
            val data = event.receivedData
            if (data.isEmpty()) return
            for (c in String(data, charset).toCharArray()) {
                queue.add(c)
            }
        }
    }

    override fun getCharset(): Charset {
        return charset
    }
}