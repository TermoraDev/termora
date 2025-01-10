package app.termora.localshell.windows

import com.sun.jna.LastErrorException
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * find the Windows PowerShell executable path

 * Note: This is a legacy implementation for **Windows PowerShell**.
 *
 * For modern PowerShell, use the [PowerShell] object.
 */
object LegacyWindowsPowerShell {
    val execPath by lazy {
        val systemRoot = System.getenv("SystemRoot") ?: return@lazy null
        val path = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe")
        if (path.exists()) {
            path
        } else {
            null
        }
    }

    internal fun runAndGet(command: String): String {
        if (execPath == null) {
            throw IllegalStateException("Windows PowerShell not found")
        }
        val process = ProcessBuilder(execPath.toString(), "-Command", command)
            .directory(null)
            .start()
        if (process.waitFor() != 0) {
            throw LastErrorException(process.exitValue())
        }
        return process.inputStream.bufferedReader().readText()
    }
}