package app.termora.localshell.windows

import com.sun.jna.LastErrorException
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * find the Windows Command Line executable path
 */
object LegacyWindowsCommandLine {
    val execPath by lazy {
        val systemRoot = System.getenv("SystemRoot") ?: return@lazy null
        val path = Path.of(systemRoot, "System32", "cmd.exe")
        if (path.exists()) {
            path
        } else {
            null
        }
    }

    internal fun runAndGet(command: String): String {
        if (execPath == null) {
            throw IllegalStateException("Windows CommandLine not found")
        }
        val process = ProcessBuilder(execPath.toString(), "/c", command)
            .directory(null)
            .start()
        if (process.waitFor() != 0) {
            throw LastErrorException(process.exitValue())
        }
        return process.inputStream.bufferedReader().readText()
    }
}