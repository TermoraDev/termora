package app.termora.tlog

import app.termora.Host
import app.termora.HostTerminalTab
import app.termora.I18n
import app.termora.randomUUID
import app.termora.terminal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.onSuccess
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.time.DateFormatUtils
import org.slf4j.LoggerFactory
import java.beans.PropertyChangeListener
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class TerminalLoggerDataListener(private val terminal: Terminal) : DataListener {
    companion object {
        /**
         * 忽略日志的标记
         */
        val IgnoreTerminalLogger = DataKey(Boolean::class)

        private val log = LoggerFactory.getLogger(TerminalLoggerDataListener::class.java)
    }

    private var coroutineScope: CoroutineScope? = null
    private var channel: Channel<String>? = null
    private var file: File? = null
    private var writer: BufferedWriter? = null

    private val isRecording = AtomicBoolean(false)
    private val isClosed = AtomicBoolean(false)

    // 监听 Recording 变化，如果已经停止录制，那么立即关闭文件
    private val terminalLoggerActionPropertyChangeListener = PropertyChangeListener { evt ->
        if (evt.propertyName == "Recording") {
            if (evt.newValue == false) {
                close()
            }
        }
    }

    private val host: Host?
        get() {
            if (terminal.getTerminalModel().hasData(HostTerminalTab.Host)) {
                return terminal.getTerminalModel().getData(HostTerminalTab.Host)
            }
            return null
        }


    init {
        terminal.addTerminalListener(object : TerminalListener {
            override fun onClose(terminal: Terminal) {
                if (isClosed.compareAndSet(false, true)) {
                    // 设置为已经关闭
                    isClosed.set(true)

                    // 移除变动监听
                    terminal.getTerminalModel().removeDataListener(this@TerminalLoggerDataListener)

                    // 关闭流
                    close()
                }
            }
        })
    }

    override fun onChanged(key: DataKey<*>, data: Any) {
        if (isClosed.get()) {
            return
        }

        // 如果忽略了，那么跳过
        if (terminal.getTerminalModel().getData(IgnoreTerminalLogger, false)) {
            return
        }

        if (key != VisualTerminal.Written && key != TextProcessor.Written) {
            return
        }

        val host = this.host ?: return
        val action = TerminalLoggerAction.getInstance()
        if (action.isRecording.not()) {
            return
        }

        try {
            if (action.isPlainText) {
                if (key == TextProcessor.Written) {
                    tryRecord(data as String, host, action)
                }
            } else if (key == VisualTerminal.Written) {
                tryRecord(data as String, host, action)
            }
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
        }
    }

    private fun tryRecord(text: String, host: Host, action: TerminalLoggerAction) {
        if (isRecording.compareAndSet(false, true)) {

            val file = createFile(host, action.getLogDir()).apply { file = this }
            val writer = BufferedWriter(FileWriter(file, false)).apply { writer = this }

            if (log.isInfoEnabled) {
                log.info("Terminal logger file: ${file.absolutePath}")
            }

            action.removePropertyChangeListener(terminalLoggerActionPropertyChangeListener)
            action.addPropertyChangeListener(terminalLoggerActionPropertyChangeListener)

            val coroutineScope =
                this.coroutineScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO).apply { coroutineScope = this }
            val channel = this.channel ?: Channel<String>(Channel.UNLIMITED).apply { channel = this }

            coroutineScope.launch {
                while (coroutineScope.isActive) {
                    channel.receiveCatching().onSuccess {
                        writer.write(it)
                    }.onFailure { e ->
                        if (log.isErrorEnabled && e is Throwable) {
                            log.error(e.message, e)
                        }
                    }
                }
            }

            val date = DateFormatUtils.format(Date(), I18n.getString("termora.date-format"))
            channel.trySend("[BEGIN] ---- $date ----").isSuccess
            channel.trySend("${ControlCharacters.LF}${ControlCharacters.CR}").isSuccess
        }

        if (isRecording.get()) {
            channel?.trySend(text)?.isSuccess
        }
    }

    private fun createFile(host: Host, dir: File): File {
        val now = DateFormatUtils.format(Date(), "HH_mm_ss_SSS")
        val filename = "${dir.absolutePath}${File.separator}${host.name}.${now}.log"
        return try {
            // 如果名称中包含 :\\n 等符号会获取失败，那么采用 ID 代替
            Paths.get(filename).toFile()
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
            try {
                Paths.get(dir.absolutePath, "${host.id}.${now}.log").toFile()
            } catch (e: Exception) {
                Paths.get(dir.absolutePath, "${randomUUID()}.${now}.log").toFile()
            }
        }
    }

    private fun close() {
        if (!isRecording.compareAndSet(true, false)) {
            return
        }

        // 移除监听
        TerminalLoggerAction.getInstance()
            .removePropertyChangeListener(terminalLoggerActionPropertyChangeListener)


        this.channel?.close()
        this.coroutineScope?.cancel()

        this.channel = null
        this.coroutineScope = null

        // write end
        runCatching {
            val date = DateFormatUtils.format(Date(), I18n.getString("termora.date-format"))
            this.writer?.write("${ControlCharacters.LF}${ControlCharacters.CR}")
            this.writer?.write("[END] ---- $date ----")
        }.onFailure {
            if (log.isErrorEnabled) {
                log.error(it.message, it)
            }
        }

        IOUtils.closeQuietly(this.writer)

        val file = this.file
        if (log.isInfoEnabled && file != null) {
            log.info("Terminal logger file: {} saved", file.absolutePath)
        }

        this.writer = null
        this.file = null


    }
}