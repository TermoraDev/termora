package app.termora.addons.zmodem

import app.termora.I18n
import app.termora.nv.FileChooser
import app.termora.terminal.ControlCharacters
import app.termora.terminal.PtyConnectorDelegate
import app.termora.terminal.StreamPtyConnector
import app.termora.terminal.Terminal
import app.termora.terminal.panel.TerminalPanel
import com.formdev.flatlaf.util.SystemInfo
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.net.io.CopyStreamEvent
import org.apache.commons.net.io.CopyStreamListener
import org.slf4j.LoggerFactory
import zmodem.FileCopyStreamEvent
import zmodem.ZModem
import zmodem.util.CustomFile
import zmodem.util.EmptyFileAdapter
import zmodem.util.FileAdapter
import zmodem.xfer.zm.util.ZModemCharacter
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.CharBuffer
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlin.math.min


/**
 * https://wiki.synchro.net/ref:zmodem
 */
class ZModemPtyConnectorAdaptor(
    private val terminal: Terminal,
    private val terminalPanel: TerminalPanel,
    private val pty: StreamPtyConnector
) : PtyConnectorDelegate(pty) {

    companion object {
        private val log = LoggerFactory.getLogger(ZModemPtyConnectorAdaptor::class.java)
    }

    private val prefix = charArrayOf(
        ZModemCharacter.ZPAD.value().toInt().toChar(),
        ZModemCharacter.ZPAD.value().toInt().toChar(),
        ZModemCharacter.ZDLE.value().toInt().toChar()
    )

    @Volatile
    private var zmodem: ZModemProcessor? = null

    override fun read(buffer: CharArray): Int {
        if (zmodem != null) {
            zmodem?.process()
            zmodem = null
        }

        val i = pty.read(buffer)
        if (i < 1) {
            return i
        }

        val e = indexOf(Arrays.copyOfRange(buffer, 0, i), prefix)
        if (e == -1) {
            return i
        }

        val zmodemFrame = Arrays.copyOfRange(buffer, e, i)

        zmodem = ZModemProcessor(
            // sz: * * 0x18 B 0 0
            // rz: * * 0x18 B 0 1
            zmodemFrame.size > 5 && zmodemFrame[5].code == 48,
            terminalPanel,
            terminal,
            ZModemInputStream(pty.input, CharBuffer.wrap(zmodemFrame).toString().toByteArray()),
            pty.output
        )

        return e
    }

    private fun indexOf(a: CharArray, b: CharArray): Int {
        if (a.size < b.size) {
            return -1
        }
        for (i in 0..a.size - b.size) {
            val range = Arrays.copyOfRange(a, i, i + b.size)
            if (range.contentEquals(b)) {
                return i
            }
        }
        return -1
    }

    override fun write(buffer: ByteArray, offset: Int, len: Int) {
        if (zmodem != null) {
            if (buffer[offset] == 0x03.toByte()) {
                zmodem?.cancel()
            }
            return
        }
        return pty.write(buffer, offset, len)
    }

    private class ZModemInputStream(private val input: InputStream, private val buffer: ByteArray) : InputStream() {
        private var index = 0
        override fun read(): Int {
            if (index < buffer.size) {
                index++
                return buffer[index - 1].toInt()
            }
            return input.read()
        }
    }

    private class ZModemProcessor(
        // 如果为 true 表示是接收（sz）文件
        private val sz: Boolean,
        private val terminalPanel: TerminalPanel,
        private val terminal: Terminal,
        input: InputStream,
        output: OutputStream
    ) : CopyStreamListener {

        private val zmodem = ZModem(input, output)
        private var lastRefreshTime = 0L

        fun process() {
            if (sz) {
                receive()
            } else {
                send()
            }
        }

        private fun receive() {
            zmodem.receive(object : Supplier<FileAdapter> {
                override fun get(): FileAdapter {
                    try {
                        val file = openFilesDialog(JFileChooser.DIRECTORIES_ONLY).firstOrNull()
                        if (file != null) {
                            FileUtils.forceMkdir(file)
                        }
                        return if (file == null) EmptyFileAdapter.instance else CustomFile(file)
                    } catch (e: Exception) {
                        if (log.isErrorEnabled) {
                            log.error(e.message, e)
                        }
                        return EmptyFileAdapter.instance
                    }
                }
            }, this)
        }

        private fun send() {

            zmodem.send({
                val files = mutableListOf<FileAdapter>()
                try {
                    files.addAll(openFilesDialog(JFileChooser.FILES_ONLY).map { CustomFile(it) })
                } catch (e: Exception) {
                    if (log.isErrorEnabled) {
                        log.error(e.message, e)
                    }
                }
                files
            }, this)
        }

        private fun refreshProgress(event: FileCopyStreamEvent) {
            val width = 24
            val skip = event.skip
            val completed = event.bytesTransferred.toLong() >= event.totalBytesTransferred
            val rate = (event.bytesTransferred * 1.0 / event.totalBytesTransferred) * 100.0
            val progress = if (completed) "100" else String.format("%.2f", min(rate, 99.99))
            val total = event.remaining + event.index - 1
            val sb = StringBuilder()
            sb.append(ControlCharacters.CR)
            sb.append(ControlCharacters.ESC).append("[0J")
            sb.append('[').append(ControlCharacters.ESC).append("[35m").append(event.index)
            sb.append(ControlCharacters.ESC).append("[39m").append('/')
            sb.append(ControlCharacters.ESC).append("[35m").append(total)
                .append(ControlCharacters.ESC).append("[39m").append(']')
            sb.append(ControlCharacters.TAB)
            sb.append(StringUtils.abbreviate(StringUtils.rightPad(event.filename, width), width))
            sb.append(ControlCharacters.TAB)
            sb.append(
                StringUtils.abbreviate(
                    StringUtils.rightPad(
                        "${event.bytesTransferred}/${event.totalBytesTransferred}",
                        width
                    ), width
                )
            )
            sb.append(ControlCharacters.TAB)

            if (event.skip) {
                sb.append("[${I18n.getString("termora.addons.zmodem.skip")}]")
            } else {
                sb.append(progress).append('%')
            }

            // 换行
            if ((completed && event.remaining > 1) || event.skip) {
                sb.append(ControlCharacters.LF)
                sb.append(ControlCharacters.CR)
            }

            if (completed && total == event.index) {
                sb.append(ControlCharacters.LF)
                sb.append(ControlCharacters.CR)
            }

            if (completed || skip) {
                SwingUtilities.invokeLater { terminal.write(sb.toString()) }
                return
            }

            val now = System.currentTimeMillis()
            if (now - lastRefreshTime > 100) {
                lastRefreshTime = now
                SwingUtilities.invokeLater { terminal.write(sb.toString()) }
            }
        }


        private fun openFilesDialog(fileSelectionMode: Int): List<File> {
            val future = CompletableFuture<List<File>>()

            SwingUtilities.invokeAndWait {
                val owner = SwingUtilities.getWindowAncestor(terminalPanel)
                val chooser = FileChooser()
                chooser.fileSelectionMode = fileSelectionMode
                chooser.allowsMultiSelection = fileSelectionMode == JFileChooser.FILES_ONLY
                if (SystemInfo.isMacOS || fileSelectionMode == JFileChooser.FILES_ONLY) {
                    future.complete(chooser.showOpenDialog(owner).get())
                } else {
                    val file = chooser.showSaveDialog(owner, String()).get()
                    if (file == null) {
                        future.complete(emptyList())
                    } else {
                        future.complete(listOf(file))
                    }
                }
            }

            return future.get()
        }

        override fun bytesTransferred(event: CopyStreamEvent) {
            if (event !is FileCopyStreamEvent) {
                return
            }
            refreshProgress(event)
        }

        override fun bytesTransferred(totalBytesTransferred: Long, bytesTransferred: Int, streamSize: Long) {
            TODO("Not yet implemented")
        }

        fun cancel() {
            zmodem.cancel()
        }
    }

}