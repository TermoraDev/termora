package app.termora.tlog

import app.termora.*
import app.termora.terminal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.onSuccess
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.time.DateFormatUtils
import org.jdesktop.swingx.action.ActionManager
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths
import java.util.*

class TerminalLoggerDataListener(private val terminal: Terminal) : DataListener {
    companion object {
        /**
         * 忽略日志的标记
         */
        val IgnoreTerminalLogger = DataKey(Boolean::class)

        private val log = LoggerFactory.getLogger(TerminalLoggerDataListener::class.java)
    }

    private val coroutineScope by lazy { CoroutineScope(Dispatchers.IO) }
    private val channel by lazy { Channel<String>(Channel.UNLIMITED) }

    private lateinit var file: File
    private lateinit var writer: BufferedWriter

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
                close()
            }
        })
    }

    override fun onChanged(key: DataKey<*>, data: Any) {
        if (key != VisualTerminal.Written) {
            return
        }

        // 如果忽略了，那么跳过
        if (terminal.getTerminalModel().getData(IgnoreTerminalLogger, false)) {
            return
        }

        val host = this.host ?: return
        val action = ActionManager.getInstance().getAction(Actions.TERMINAL_LOGGER)
        if (action !is TerminalLoggerAction || !action.isRecording) {
            return
        }

        // 尝试记录
        tryRecord(data as String, host, action)
    }

    private fun tryRecord(text: String, host: Host, action: TerminalLoggerAction) {
        if (!::writer.isInitialized) {

            file = createFile(host, action.getLogDir())

            writer = BufferedWriter(FileWriter(file, false))

            if (log.isInfoEnabled) {
                log.info("Terminal logger file: ${file.absolutePath}")
            }

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

        channel.trySend(text).isSuccess
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
                Paths.get(dir.absolutePath, "${UUID.randomUUID().toSimpleString()}.${now}.log").toFile()
            }
        }
    }

    private fun close() {
        if (!::writer.isInitialized) {
            return
        }

        channel.close()
        coroutineScope.cancel()

        // write end
        runCatching {
            val date = DateFormatUtils.format(Date(), I18n.getString("termora.date-format"))
            writer.write("${ControlCharacters.LF}${ControlCharacters.CR}")
            writer.write("[END] ---- $date ----")
        }.onFailure {
            if (log.isErrorEnabled) {
                log.info(it.message, it)
            }
        }


        IOUtils.closeQuietly(writer)

        if (log.isInfoEnabled) {
            log.info("Terminal logger file: {} saved", file.absolutePath)
        }
    }
}