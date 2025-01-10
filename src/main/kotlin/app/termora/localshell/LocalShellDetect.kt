package app.termora.localshell

import app.termora.CommandLineArgs
import app.termora.db.Database
import app.termora.localshell.windows.LegacyWindowsCommandLine
import app.termora.localshell.windows.LegacyWindowsPowerShell
import app.termora.localshell.windows.PowerShell
import app.termora.localshell.windows.Wsl
import com.formdev.flatlaf.util.SystemInfo
import com.sun.jna.LastErrorException
import java.nio.file.Path
import kotlin.io.path.pathString
import org.slf4j.LoggerFactory

sealed interface LocalShellProvider {
    val localShell: LocalShell?

    object FollowGlobal : LocalShellProvider {
        override val localShell: LocalShell? get() = makeProviderByIdentificationString(Database.instance.terminal.localShell)?.localShell
    }

    class Specify internal constructor(override val localShell: LocalShell) : LocalShellProvider
    class CustomCommandLineArgs(val commandLine: CommandLineArgs) : LocalShellProvider {
        override val localShell by lazy {
            LocalShell(
                LocalShellId(LocalShellId.Group.CUSTOM, commandLine.commandLineString),
                commandLine.commandLineString,
                Path.of(commandLine.commandLine.firstOrNull() ?: ""),
                commandLine.commandLine.drop(1)
            )
        }
    }

    companion object {
        val EmptyCustomCommandLineArgs = CustomCommandLineArgs(CommandLineArgs.of(emptyList()))

        fun makeProviderByIdentificationString(identificationString: String?): LocalShellProvider? {
            if (identificationString == null) return null
            val id = LocalShellId.fromString(identificationString) ?: return null
            if (id.group == LocalShellId.Group.CUSTOM) {
                return CustomCommandLineArgs(CommandLineArgs.of(id.id))
            }
            val localShell = LocalShellDetect.getLocalShellById(id)
            if (localShell != null) {
                return localShell.provider
            }
            return null
        }
    }
}

data class LocalShellId(val group: Group, val id: String) {
    fun asIdentificationString(): String {
        return "${group.id}:$id"
    }

    override fun toString(): String {
        return asIdentificationString()
    }

    enum class Group(val id: String) {
        PATH("path"),
        WIN_BUILTIN("win-builtin"),
        WSL("wsl"),
        PWSH("pwsh"),
        CUSTOM("custom"),
    }

    companion object {
        private val groupMap = Group.entries.associateBy { it.id }
        fun fromString(id: String): LocalShellId? {
            val (group, shellId) = id.split(":", limit = 2)
            val groupEnum = groupMap[group] ?: return null
            return LocalShellId(groupEnum, shellId)
        }
    }
}

data class LocalShell(
    val id: LocalShellId,
    val displayName: String,
    val executablePath: Path,
    val arguments: List<String> = emptyList(),
    val homeDirectory: String? = null
) {
    val provider by lazy { LocalShellProvider.Specify(this) }

    override fun toString(): String {
        return buildString {
            append(displayName)
            append(" (")
            append(executablePath.pathString)
            arguments.forEach {
                append(" ")
                append(it)
            }
            append(")")
        }
    }
}

/**
 * Detects all available local shells
 * Such as Windows PowerShell, Windows Command Line Prompt, WSL, PowerShell, etc.
 */
object LocalShellDetect {
    private val localShellMaps: Map<LocalShellId, LocalShell> by lazy {
        if (SystemInfo.isWindows) {
            detectWindowsLocalShells()
        } else {
            detectUnixLocalShells()
        }.associateBy { it.id }
    }

    private fun detectUnixLocalShells(): List<LocalShell> {
        return runCatching {
            val process = ProcessBuilder("cat", "/etc/shells").start()
            if (process.waitFor() != 0) {
                throw LastErrorException(process.exitValue())
            }
            process.inputStream.use { it.readAllBytes() }
                .toString(Charsets.UTF_8)
                .lines()
                .filter { e -> !e.trimStart().startsWith('#') }
                .filter { e -> e.isNotBlank() }
                .map { it.trim() }
        }.recover {
            logger.error("Failed to detect local shells: ${it.message}", it)
            val shell = System.getenv("SHELL")
            if (shell != null && shell.isNotBlank()) {
                listOf(shell)
            } else {
                emptyList()
            }
        }.map {
            it.ifEmpty { listOf("/bin/bash", "/bin/csh", "/bin/dash", "/bin/ksh", "/bin/sh", "/bin/tcsh", "/bin/zsh") }
                .map { shellPath -> LocalShell(LocalShellId(LocalShellId.Group.PATH, shellPath), shellPath.substringAfterLast('/'), Path.of(shellPath)) }
        }.getOrElse { emptyList() }
    }

    private fun detectWindowsLocalShells(): List<LocalShell> {
        return buildList {
            LegacyWindowsPowerShell.execPath?.let {
                add(LocalShell(LocalShellId(LocalShellId.Group.WIN_BUILTIN, "powershell"), "Windows PowerShell", it))
            }
            LegacyWindowsCommandLine.execPath?.let {
                add(LocalShell(LocalShellId(LocalShellId.Group.WIN_BUILTIN, "cmd"), "Windows CommandLine Prompt", it))
            }
            runCatching {
                PowerShell.collectPowerShellInstances().forEach {
                    add(LocalShell(LocalShellId(LocalShellId.Group.PWSH, it.id()), it.name(), it.executablePath))
                }
            }.onFailure {
                logger.error("Failed to detect PowerShell instances: ${it.message}", it)
            }
            runCatching {
                Wsl.enumerateDistributions().forEach {
                    add(LocalShell(LocalShellId(LocalShellId.Group.WSL, it.id()), "WSL - ${it.distributionName}", it.wslExecutablePath, it.startArguments))
                }
            }.onFailure {
                logger.error("Failed to detect WSL distributions: ${it.message}", it)
            }
        }
    }

    fun getLocalShellById(id: LocalShellId): LocalShell? {
        val detected = localShellMaps[id]
        if (detected != null) {
            return detected
        }
        if (id.group == LocalShellId.Group.PATH) {
            return LocalShell(id, id.id, Path.of(id.id))
        }
        return null
    }

    fun getSupportAllLocalShell(): Collection<LocalShell> {
        return localShellMaps.values
    }

    private val logger = LoggerFactory.getLogger(LocalShellDetect::class.java)
}