package app.termora

import app.termora.database.DatabaseManager
import app.termora.macro.MacroPtyConnector
import app.termora.terminal.PtyConnector
import app.termora.terminal.PtyConnectorDelegate
import app.termora.terminal.PtyProcessConnector
import com.pty4j.PtyProcessBuilder
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.slf4j.LoggerFactory
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*

class PtyConnectorFactory : Disposable {
    private val ptyConnectors = Collections.synchronizedList(mutableListOf<PtyConnector>())
    private val database get() = DatabaseManager.getInstance()

    companion object {
        private val log = LoggerFactory.getLogger(PtyConnectorFactory::class.java)
        fun getInstance(): PtyConnectorFactory {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(PtyConnectorFactory::class) { PtyConnectorFactory() }
        }
    }

    fun createPtyConnector(
        rows: Int = 24, cols: Int = 80,
        env: Map<String, String> = emptyMap(),
        charset: Charset = StandardCharsets.UTF_8
    ): PtyConnector {
        val command = database.terminal.localShell
        val commands = mutableListOf(command)
        if (SystemUtils.IS_OS_UNIX) {
            commands.add("-l")
        }
        return createPtyConnector(
            commands = commands.toTypedArray(),
            rows = rows,
            cols = cols,
            env = env,
            charset = charset
        )
    }

    fun createPtyConnector(
        commands: Array<String>,
        rows: Int = 24, cols: Int = 80,
        env: Map<String, String> = emptyMap(),
        directory: String = SystemUtils.USER_HOME,
        charset: Charset = StandardCharsets.UTF_8,
    ): PtyConnector {
        val envs = mutableMapOf<String, String>()
        envs.putAll(System.getenv())
        envs["TERM"] = "xterm-256color"
        envs.putAll(env)

        if (SystemUtils.IS_OS_UNIX) {
            if (!envs.containsKey("LANG")) {
                val locale = Locale.getDefault()
                if (StringUtils.isNoneBlank(locale.language, locale.country)) {
                    envs["LANG"] = "${locale.language}_${locale.country}.${Charset.defaultCharset().name()}"
                } else {
                    envs["LANG"] = "en_US.UTF-8"
                }
            }
        }

        if (log.isDebugEnabled) {
            log.debug("command: {} , envs: {}", commands.joinToString(" "), envs)
        }

        val ptyProcess = PtyProcessBuilder(commands)
            .setEnvironment(envs)
            .setInitialRows(rows)
            .setInitialColumns(cols)
            .setConsole(false)
            .setDirectory(StringUtils.defaultIfBlank(directory, SystemUtils.USER_HOME))
            .setCygwin(false)
            .setUseWinConPty(SystemUtils.IS_OS_WINDOWS)
            .setRedirectErrorStream(false)
            .setWindowsAnsiColorEnabled(false)
            .setUnixOpenTtyToPreserveOutputAfterTermination(false)
            .setSpawnProcessUsingJdkOnMacIntel(true).start()

        return decorate(PtyProcessConnector(ptyProcess, charset))
    }

    fun decorate(ptyConnector: PtyConnector): PtyConnector {
        // 宏
        val macroPtyConnector = MacroPtyConnector(ptyConnector)
        // 集成自动删除
        val autoRemovePtyConnector = AutoRemovePtyConnector(macroPtyConnector)
        ptyConnectors.add(autoRemovePtyConnector)
        return autoRemovePtyConnector
    }

    private inner class AutoRemovePtyConnector(connector: PtyConnector) : PtyConnectorDelegate(connector) {
        override fun close() {
            ptyConnectors.remove(this)
            super.close()
        }
    }
}