package app.termora.localshell.windows

import java.nio.file.Path
import kotlin.io.path.exists
import org.slf4j.LoggerFactory

data class WslDistribution(val wslExecutablePath: Path, val guid: String, val distributionName: String, val startArguments: List<String>) {
    fun id(): String {
        return "wsl:$guid"
    }
}

/**
 * Windows Subsystem for Linux (WSL) utilities
 */
object Wsl {
    private val osVersion by lazy {
        runCatching {
            LegacyWindowsPowerShell.runAndGet("[System.Environment]::OSVersion.Version.Build")
        }.onFailure {
            println("Failed to get OS version: ${it.message}")
        }.getOrNull()
    }

    private fun isWslDashDashCdAvailableForLinuxPaths(): Boolean {
        return (osVersion?.trim()?.toIntOrNull() ?: 0) >= 19041
    }

    fun enumerateDistributions(): List<WslDistribution> {
        val systemDirPath = System.getenv("SystemRoot") ?: return emptyList()
        val wslExePath = Path.of(systemDirPath, "System32", "wsl.exe")
        if (!wslExePath.exists()) {
            return emptyList()
        }
        val distroGuids = runCatching {
            LegacyWindowsPowerShell.runAndGet("(Get-ChildItem -Path HKCU:\\$RegKeyLxss | Where-Object {\$_.PSIsContainer}).Name")
        }.onFailure {
            logger.error("Failed to enumerate WSL distributions: ${it.message}", it)
        }.getOrNull()?.lines()?.filter { it.isNotBlank() }?.mapNotNull { it.substringAfterLast('\\') } ?: emptyList()

        val defaultWslHome = if (isWslDashDashCdAvailableForLinuxPaths()) {
            WslHomeDirectory
        } else {
            System.getenv("USERPROFILE")
        }

        return distroGuids.mapNotNull { distroGuid ->
            val modernValue = runCatching {
                LegacyWindowsPowerShell.runAndGet("Get-ItemPropertyValue -Path 'HKCU:\\$RegKeyLxss\\$distroGuid' -Name $RegKeyModern")
            }.getOrNull()?.toIntOrNull() ?: 0
            if (modernValue == 1) {
                return@mapNotNull null
            }

            val distroName = runCatching {
                LegacyWindowsPowerShell.runAndGet("Get-ItemPropertyValue -Path 'HKCU:\\$RegKeyLxss\\$distroGuid' -Name $RegValueDistributionName")
            }.onFailure {
                logger.error("Failed to get distribution name for $distroGuid: ${it.message}", it)
            }.getOrNull()?.trim()

            if (distroName == null) {
                return@mapNotNull null
            }

            if (distroName.startsWith(DockerDistributionPrefix) || distroName.startsWith(RancherDistributionPrefix)) {
                // Docker for Windows and Rancher for Windows creates some utility distributions to handle Docker commands.
                // Pursuant to https://github.com/microsoft/terminal/issues/3556, because they are _not_ user-facing we want to hide them.
                return@mapNotNull null
            }

            WslDistribution(wslExePath, distroGuid, distroName, listOf("-d", distroName, "--cd", defaultWslHome))
        }.also {
            logger.debug("Enumerated WSL ${it.size} distributions: ${it.joinToString { it.distributionName }}")
        }
    }

    private const val WslHomeDirectory = "~"
    private const val DockerDistributionPrefix = "docker-desktop"
    private const val RancherDistributionPrefix = "rancher-desktop"

    // The WSL entries are structured as such:
    // HKCU\Software\Microsoft\Windows\CurrentVersion\Lxss
    //   ⌞ {distroGuid}
    //     ⌞ DistributionName: {the name}
    private const val RegKeyLxss = "Software\\Microsoft\\Windows\\CurrentVersion\\Lxss"
    private const val RegValueDistributionName = "DistributionName"
    private const val RegKeyModern = "Modern"


    private val logger = LoggerFactory.getLogger(Wsl::class.java)
}