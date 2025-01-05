package app.termora.localshell.windows

import app.termora.Application.ohMyJson
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

private enum class PowerShellCoreFlags(val code: Int) {
    None(0),

    // These flags are used as a sort key, so they encode some native ordering.
    // They are ordered such that the "most important" flags have the largest
    // impact on the sort space. For example, since we want Preview to be very polar
    // we give it the highest flag value.
    // The "ideal" powershell instance has 0 flags (stable, native, Program Files location)
    //
    // With this ordering, the sort space ends up being (for PowerShell 6)
    // (numerically greater values are on the left; this is flipped in the final sort)
    //
    // <-- Less Valued .................................... More Valued -->
    // |                 All instances of PS 6                 | All PS7  |
    // |          Preview          |          Stable           | ~~~      |
    // |  Non-Native | Native      |  Non-Native | Native      | ~~~      |
    // | Trd  | Pack | Trd  | Pack | Trd  | Pack | Trd  | Pack | ~~~      |
    // (where Pack is a stand-in for store, scoop, dotnet, though they have their own orders,
    // and Trd is a stand-in for "Traditional" (Program Files))
    //
    // In short, flags with larger magnitudes are pushed further down (therefore valued less)

    // distribution method (choose one)
    Store(1 shl 0),// distributed via the store
    Scoop(1 shl 1), // installed via Scoop
    Dotnet(1 shl 2), // installed as a dotnet global tool
    Traditional(1 shl 3),// installed in traditional Program Files locations

    // native architecture (choose one)
    WOWARM(1 shl 4), // non-native (Windows-on-Windows, ARM variety)
    WOWx86(1 shl 5), // non-native (Windows-on-Windows, x86 variety)

    // build type (choose one)
    Preview(1 shl 6), // preview version
    ;

    fun isSet(flags: Int): Boolean {
        return flags and code == code
    }
}

class PowerShellInstance(
    val majorVersion: Int,
    val powerShellCoreFlags: Int,
    val executablePath: Path,
) : Comparable<PowerShellInstance> {
    override fun compareTo(other: PowerShellInstance): Int {
        // First, compare major version
        if (majorVersion != other.majorVersion) {
            return majorVersion.compareTo(other.majorVersion)
        }
        // Next, compare flags
        if (powerShellCoreFlags != other.powerShellCoreFlags) {
            // flags are inverted because "0" is ideal; see above
            return other.powerShellCoreFlags.compareTo(powerShellCoreFlags)
        }
        // fall back to path sorting
        return executablePath.compareTo(other.executablePath)
    }

    fun id(): String {
        return "pwsh:${majorVersion}:${powerShellCoreFlags}"
    }

    fun name(): String {
        return buildString {
            append("PowerShell")

            if (PowerShellCoreFlags.Store.isSet(powerShellCoreFlags)) {
                if (PowerShellCoreFlags.Preview.isSet(powerShellCoreFlags)) {
                    append(" Preview")
                }
                append(" (msix)")
            } else if (PowerShellCoreFlags.Dotnet.isSet(powerShellCoreFlags)) {
                append(" (dotnet global)")
            } else if (PowerShellCoreFlags.Scoop.isSet(powerShellCoreFlags)) {
                append(" (scoop)")
            } else {
                if (majorVersion < 7) {
                    append(" Core")
                }
                if (majorVersion != 0) {
                    append(" $majorVersion")
                }
                if (PowerShellCoreFlags.Preview.isSet(powerShellCoreFlags)) {
                    append(" Preview")
                }
                if (PowerShellCoreFlags.WOWx86.isSet(powerShellCoreFlags)) {
                    append(" (x86)")
                }
                if (PowerShellCoreFlags.WOWARM.isSet(powerShellCoreFlags)) {
                    append(" (ARM)")
                }
            }
        }
    }
}

/**
 * A collection of functions to find PowerShell instances on the system.
 *
 * Note: Here not **Windows PowerShell**, if you want to find **Windows PowerShell** use [LegacyWindowsPowerShell].
 *
 * see: [PowerShell](https://github.com/PowerShell/PowerShell)
 */
object PowerShell {
    /**
     * Finds all powershell instances with the traditional layout under a directory.
     * The "traditional" directory layout requires that pwsh.exe exist in a versioned directory, as in
     * ROOT\6\pwsh.exe
     * @param directory the directory under which to search
     * @param flags flags to apply to all found instances
     * @return the list of powershell instances found
     */
    private fun accumulateTraditionalLayoutPowerShellInstancesInDirectory(directory: Path, flags: Int): List<PowerShellInstance> {
        val out = mutableListOf<PowerShellInstance>()
        if (directory.exists()) {
            for (versionedPath in Files.list(directory)) {
                val executable = versionedPath.resolve("pwsh.exe")
                if (executable.exists()) {
                    val preview = versionedPath.fileName.toString().contains("-preview")
                    val previewFlag = if (preview) PowerShellCoreFlags.Preview.code else PowerShellCoreFlags.None.code
                    out.add(
                        PowerShellInstance(
                            majorVersion = versionedPath.fileName.toString().toInt(),
                            powerShellCoreFlags = PowerShellCoreFlags.Traditional.code or flags or previewFlag,
                            executablePath = executable
                        )
                    )
                }
            }
        }
        return out
    }

    /**
     * Finds the store package, if one exists, for a given package family name
     * @param packageName the package name
     * @param packageFamilyName the package family name
     * @return the package version, or null
     */
    private fun getStorePackage(packageName: String, packageFamilyName: String): String? {
        // call powershell to get the package
        val json = runCatching {
            LegacyWindowsPowerShell.runAndGet("Get-AppxPackage -Name $packageName | ConvertTo-Json")
        }.onFailure {
            logger.error("Failed to get package: $packageName", it)
        }.getOrNull() ?: return null
        logger.debug("Get-AppxPackage of package [${packageName}] output: $json")
        val element = ohMyJson.parseToJsonElement(json)
        val pfn = element.jsonObject["PackageFamilyName"]?.jsonPrimitive?.contentOrNull
        if (pfn != packageFamilyName) {
            logger.error("PackageFamilyName mismatch with expected value: $pfn, expected: $packageFamilyName")
            return null
        }
        val version = element.jsonObject["Version"]?.jsonPrimitive?.contentOrNull
        if (version == null) {
            logger.error("Version not found in package: $packageName")
            return null
        }
        return version
    }

    /**
     * Finds all powershell instances that have App Execution Aliases in the standard location
     * @return the list of powershell instances found
     */
    private fun accumulateStorePowerShellInstances(): List<PowerShellInstance> {
        val results = mutableListOf<PowerShellInstance>()
        val localAppDataFolder = System.getenv("LOCALAPPDATA") ?: return results

        val appExecAliasPath = Path.of(localAppDataFolder, "Microsoft", "WindowsApps")
        if (appExecAliasPath.exists()) {
            // App execution aliases for preview powershell
            val previewPath = appExecAliasPath.resolve(POWERSHELL_PREVIEW_PFN)
            if (previewPath.exists()) {
                val previewPackageVersion = getStorePackage(POWERSHELL_PREVIEW_PN, POWERSHELL_PREVIEW_PFN)
                if (previewPackageVersion != null) {
                    results.add(
                        PowerShellInstance(
                            majorVersion = previewPackageVersion.toInt(),
                            powerShellCoreFlags = PowerShellCoreFlags.Store.code or PowerShellCoreFlags.Preview.code,
                            executablePath = previewPath.resolve(PWSH_EXE)
                        )
                    )
                }
            }

            // App execution aliases for stable powershell
            val gaPath = appExecAliasPath.resolve(POWERSHELL_PFN)
            if (gaPath.exists()) {
                val gaPackageVersion = getStorePackage(POWERSHELL_PN, POWERSHELL_PFN)
                if (gaPackageVersion != null) {
                    results.add(
                        PowerShellInstance(
                            majorVersion = gaPackageVersion.toInt(),
                            powerShellCoreFlags = PowerShellCoreFlags.Store.code,
                            executablePath = gaPath.resolve(PWSH_EXE)
                        )
                    )
                }
            }
        }
        return results
    }

    /**
     * Finds a powershell instance that's just a pwsh.exe in a folder.
     * This function cannot determine the version number of such a powershell instance.
     * @param directory the directory under which to search
     * @param flags flags to apply to all found instances
     * @return the list of powershell instances found
     */
    private fun accumulatePwshExeInDirectory(directory: Path, flags: Int): List<PowerShellInstance> {
        val out = mutableListOf<PowerShellInstance>()
        val pwshPath = directory.resolve(PWSH_EXE)
        if (pwshPath.exists()) {
            out.add(
                PowerShellInstance(
                    majorVersion = 0, /* we can't tell */
                    powerShellCoreFlags = flags,
                    executablePath = pwshPath
                )
            )
        }
        return out
    }

    /**
     * Builds a comprehensive priority-ordered list of powershell instances.
     * @return a comprehensive priority-ordered list of powershell instances.
     */
    fun collectPowerShellInstances(): List<PowerShellInstance> {
        val versions = mutableListOf<PowerShellInstance>()

        System.getenv("ProgramFiles")?.let {
            versions.addAll(accumulateTraditionalLayoutPowerShellInstancesInDirectory(Path.of(it, "PowerShell"), PowerShellCoreFlags.None.code))
        }
        System.getenv("ProgramFiles(x86)")?.let {
            versions.addAll(accumulateTraditionalLayoutPowerShellInstancesInDirectory(Path.of(it, "PowerShell"), PowerShellCoreFlags.WOWx86.code))
        }
        System.getenv("ProgramFiles(Arm)")?.let {
            versions.addAll(accumulateTraditionalLayoutPowerShellInstancesInDirectory(Path.of(it, "PowerShell"), PowerShellCoreFlags.WOWARM.code))
        }

        versions.addAll(accumulateStorePowerShellInstances())

        System.getenv("USERPROFILE")?.let {
            versions.addAll(accumulatePwshExeInDirectory(Path.of(it, ".dotnet", "tools"), PowerShellCoreFlags.Dotnet.code))
            versions.addAll(accumulatePwshExeInDirectory(Path.of(it, "scoop", "shims"), PowerShellCoreFlags.Scoop.code))
        }

        versions.sortDescending()

        logger.info("Found PowerShell ${versions.size} instance(s): ${versions.joinToString { it.name() }}")

        return versions
    }


    private const val POWERSHELL_PN = "Microsoft.PowerShell"
    private const val POWERSHELL_PFN = "Microsoft.PowerShell_8wekyb3d8bbwe"
    private const val POWERSHELL_PREVIEW_PN = "Microsoft.PowerShellPreview"
    private const val POWERSHELL_PREVIEW_PFN = "Microsoft.PowerShellPreview_8wekyb3d8bbwe"
    private const val PWSH_EXE = "pwsh.exe"

    private val logger = LoggerFactory.getLogger(PowerShell::class.java)
}