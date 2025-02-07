package app.termora

import app.termora.terminal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.lang3.exception.ExceptionUtils
import org.slf4j.LoggerFactory
import java.awt.event.KeyEvent
import javax.swing.JComponent
import kotlin.time.Duration.Companion.milliseconds

abstract class PtyHostTerminalTab(
    windowScope: WindowScope,
    host: Host,
    terminal: Terminal = TerminalFactory.getInstance(windowScope).createTerminal()
) : HostTerminalTab(windowScope, host, terminal) {

    companion object {
        private val log = LoggerFactory.getLogger(PtyHostTerminalTab::class.java)
    }


    private var readerJob: Job? = null
    private val ptyConnectorDelegate = PtyConnectorDelegate()

    protected val terminalPanel =
        TerminalPanelFactory.getInstance(windowScope).createTerminalPanel(terminal, ptyConnectorDelegate)
    protected val ptyConnectorFactory get() = PtyConnectorFactory.getInstance(windowScope)

    init {
        terminal.getTerminalModel().setData(DataKey.PtyConnector, ptyConnectorDelegate)
    }

    override fun start() {
        coroutineScope.launch(Dispatchers.IO) {

            try {

                withContext(Dispatchers.Swing) {
                    // clear terminal
                    terminal.clearScreen()
                }

                // 开启 PTY
                val ptyConnector = openPtyConnector()
                ptyConnectorDelegate.ptyConnector = ptyConnector

                // 开启 reader
                startPtyConnectorReader()

                // 启动命令
                if (host.options.startupCommand.isNotBlank()) {
                    coroutineScope.launch(Dispatchers.IO) {
                        delay(250.milliseconds)
                        withContext(Dispatchers.Swing) {
                            val charset = ptyConnector.getCharset()
                            ptyConnector.write(host.options.startupCommand.toByteArray(charset))
                            ptyConnector.write(
                                terminal.getKeyEncoder().encode(TerminalKeyEvent(KeyEvent.VK_ENTER))
                                    .toByteArray(charset)
                            )
                        }
                    }
                }

                if (log.isInfoEnabled) {
                    log.info("Host: {} started", host.name)
                }

            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }

                // 失败关闭
                stop()

                withContext(Dispatchers.Swing) {
                    terminal.write("\r\n${ControlCharacters.ESC}[31m")
                    terminal.write(ExceptionUtils.getRootCauseMessage(e))
                    terminal.write("${ControlCharacters.ESC}[0m")
                }
            }

        }
    }

    override fun canReconnect(): Boolean {
        return true
    }

    override fun reconnect() {
        stop()
        start()
    }

    override fun getJComponent(): JComponent {
        return terminalPanel
    }

    open fun startPtyConnectorReader() {
        readerJob?.cancel()
        readerJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                PtyConnectorReader(ptyConnectorDelegate, terminal).start()
            } catch (e: Exception) {
                log.error(e.message, e)
            }
        }
    }

    open fun stop() {
        readerJob?.cancel()
        ptyConnectorDelegate.close()

        if (log.isInfoEnabled) {
            log.info("Host: {} stopped", host.name)
        }
    }

    override fun dispose() {
        stop()
        super.dispose()


        if (log.isInfoEnabled) {
            log.info("Host: {} disposed", host.name)
        }
    }

    open fun getPtyConnector(): PtyConnector {
        return ptyConnectorDelegate
    }

    abstract suspend fun openPtyConnector(): PtyConnector
}