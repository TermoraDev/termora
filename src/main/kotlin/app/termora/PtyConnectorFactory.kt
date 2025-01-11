package app.termora

import app.termora.macro.MacroPtyConnector
import app.termora.terminal.PtyConnector
import app.termora.terminal.PtyConnectorDelegate
import app.termora.terminal.PtyProcessConnector
import com.pty4j.PtyProcessBuilder
import org.apache.commons.lang3.SystemUtils
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*

class PtyConnectorFactory : Disposable {
    private val ptyConnectors = Collections.synchronizedList(mutableListOf<PtyConnector>())
    private val database get() = Database.getDatabase()

    companion object {
        fun getInstance(scope: Scope): PtyConnectorFactory {
            return scope.getOrCreate(PtyConnectorFactory::class) { PtyConnectorFactory() }
        }
    }

    fun createPtyConnector(
        scope: Scope,
        rows: Int = 24, cols: Int = 80,
        env: Map<String, String> = emptyMap(),
        charset: Charset = StandardCharsets.UTF_8
    ): PtyConnector {
        val envs = mutableMapOf<String, String>()
        envs.putAll(System.getenv())
        envs["TERM"] = "xterm-256color"
        envs.putAll(env)

        val command = database.terminal.localShell
        val commands = mutableListOf(command)
        if (SystemUtils.IS_OS_UNIX) {
            commands.add("-l")
        }

        val ptyProcess = PtyProcessBuilder(commands.toTypedArray())
            .setEnvironment(envs)
            .setInitialRows(rows)
            .setInitialColumns(cols)
            .setConsole(false)
            .setDirectory(SystemUtils.USER_HOME)
            .setCygwin(false)
            .setUseWinConPty(SystemUtils.IS_OS_WINDOWS)
            .setRedirectErrorStream(false)
            .setWindowsAnsiColorEnabled(false)
            .setUnixOpenTtyToPreserveOutputAfterTermination(false)
            .setSpawnProcessUsingJdkOnMacIntel(true).start()

        return decorate(scope, PtyProcessConnector(ptyProcess, charset))
    }

    fun decorate(scope: Scope, ptyConnector: PtyConnector): PtyConnector {
        // 集成转发，如果PtyConnector支持转发那么应该在当前注释行前面代理
        val multiplePtyConnector = MultiplePtyConnector(scope, ptyConnector)
        // 宏应该在转发前面执行，不然会导致重复录制
        val macroPtyConnector = MacroPtyConnector(multiplePtyConnector)
        // 集成自动删除
        val autoRemovePtyConnector = AutoRemovePtyConnector(macroPtyConnector)
        ptyConnectors.add(autoRemovePtyConnector)
        return autoRemovePtyConnector
    }

    fun getPtyConnectors(): List<PtyConnector> {
        return ptyConnectors
    }

    private inner class AutoRemovePtyConnector(connector: PtyConnector) : PtyConnectorDelegate(connector) {
        override fun close() {
            ptyConnectors.remove(this)
            super.close()
        }
    }
}