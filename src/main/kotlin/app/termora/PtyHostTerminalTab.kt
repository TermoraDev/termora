package app.termora

import app.termora.actions.DataProviders
import app.termora.plugin.internal.AltKeyModifier
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
    terminal: Terminal = TerminalFactory.getInstance().createTerminal()
) : HostTerminalTab(windowScope, host, terminal) {

    companion object {
        private val log = LoggerFactory.getLogger(PtyHostTerminalTab::class.java)
    }


    private var readerJob: Job? = null
    private val ptyConnectorDelegate = PtyConnectorDelegate()
    protected val terminalPanel = TerminalPanelFactory.getInstance().createTerminalPanel(
        this,
        terminal, ptyConnectorDelegate
    )
    protected val ptyConnectorFactory get() = PtyConnectorFactory.getInstance()

    override fun start() {
        coroutineScope.launch(Dispatchers.IO) {

            try {

                withContext(Dispatchers.Swing) {
                    // clear terminal
                    terminal.clearScreen()
                }

                // 开启 PTY
                val ptyConnector = loginScriptsPtyConnector(host, openPtyConnector())
                ptyConnectorDelegate.ptyConnector = ptyConnector

                // 开启 reader
                startPtyConnectorReader()

                // 修饰
                terminalKeyModifiers()

                // 启动命令
                if (host.options.startupCommand.isNotBlank()) {
                    coroutineScope.launch(Dispatchers.IO) {
                        delay(250.milliseconds)
                        withContext(Dispatchers.Swing) {
                            val charset = ptyConnector.getCharset()
                            sendStartupCommand(ptyConnector, host.options.startupCommand.toByteArray(charset))
                            sendStartupCommand(
                                ptyConnector,
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

    /**
     * 登录脚本
     */
    open fun loginScriptsPtyConnector(host: Host, ptyConnector: PtyConnector): PtyConnector {
        val loginScripts = host.options.loginScripts.toMutableList()
        if (loginScripts.isEmpty()) {
            return ptyConnector
        }

        return object : PtyConnectorDelegate(ptyConnector) {
            override fun read(buffer: CharArray): Int {
                val len = super.read(buffer)

                // 获取一个匹配的登录脚本
                val scripts = runCatching { popLoginScript(buffer, len) }.getOrNull() ?: return len
                if (scripts.isEmpty()) return len

                for (script in scripts) {
                    // send
                    write(script.send.toByteArray(getCharset()))

                    // send \r or \n
                    val enter = terminal.getKeyEncoder().encode(TerminalKeyEvent(KeyEvent.VK_ENTER))
                        .toByteArray(getCharset())
                    write(enter)
                }


                return len
            }

            private fun popLoginScript(buffer: CharArray, len: Int): List<LoginScript> {
                if (loginScripts.isEmpty()) return emptyList()
                if (len < 1) return emptyList()

                val scripts = mutableListOf<LoginScript>()
                val text = String(buffer, 0, len)
                val iterator = loginScripts.iterator()
                while (iterator.hasNext()) {
                    val script = iterator.next()
                    if (script.expect.isEmpty()) {
                        scripts.add(script)
                        iterator.remove()
                        continue
                    } else if (script.regex) {
                        val regex = if (script.matchCase) script.expect.toRegex()
                        else script.expect.toRegex(RegexOption.IGNORE_CASE)
                        if (regex.matches(text)) {
                            scripts.add(script)
                            iterator.remove()
                            continue
                        }
                    } else {
                        if (text.contains(script.expect, script.matchCase.not())) {
                            scripts.add(script)
                            iterator.remove()
                            continue
                        }
                    }
                    break
                }

                return scripts
            }
        }
    }

    open fun sendStartupCommand(ptyConnector: PtyConnector, bytes: ByteArray) {
        ptyConnector.write(bytes)
    }

    open fun terminalKeyModifiers() {
        val altModifier = host.options.extras["altModifier"]
        if (altModifier == AltKeyModifier.CharactersPrecededByESC.name) {
            terminalModel.setData(DataKey.AltModifier, AltKeyModifier.CharactersPrecededByESC)
        } else {
            terminalModel.setData(DataKey.AltModifier, AltKeyModifier.EightBit)
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
        Disposer.dispose(terminalPanel)
        super.dispose()

        if (log.isInfoEnabled) {
            log.info("Host: {} disposed", host.name)
        }
    }

    open fun getPtyConnector(): PtyConnector {
        return ptyConnectorDelegate
    }

    abstract suspend fun openPtyConnector(): PtyConnector

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        if (dataKey == DataProviders.TerminalPanel) {
            return terminalPanel as T?
        } else if (dataKey == DataProviders.TerminalWriter) {
            return terminalPanel.getData(DataKey.TerminalWriter) as T?
        }
        return super.getData(dataKey)
    }
}