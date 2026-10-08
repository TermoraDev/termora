package app.termora.plugin.internal.cli

import app.termora.Application
import app.termora.AppLayout
import com.formdev.flatlaf.util.SystemInfo
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import org.apache.commons.lang3.SystemUtils
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * 把 termora-cli 安装进用户 PATH。
 * 生成 wrapper（用当前运行的 java.home 与 classpath 跑 CliMainKt），放 <baseDataDir>/bin，
 * 再加入用户 PATH：Windows 写注册表 HKCU\Environment\Path（REG_EXPAND_SZ，去重）；
 * macOS/Linux 软链到 ~/.local/bin。不广播 WM_SETTINGCHANGE（新终端从注册表读最新 PATH 即可）。
 */
object PathInstaller {
    const val CLI_NAME = "termora-cli"
    private const val MAIN_CLASS = "app.termora.cli.CliMainKt"

    /** 开发版（./gradlew :run）下 appPath 为空；wrapper 用当前 classpath，重新构建后可能失效 */
    fun isDev(): Boolean = Application.getAppPath().isBlank()

    /** Windows Zip 便携/绿色版：移动文件夹或换盘符后，wrapper 内写死的绝对路径与系统 PATH 项都会失效 */
    fun isPortable(): Boolean = SystemInfo.isWindows && Application.getLayout() == AppLayout.Zip

    fun binDir(): File = File(Application.getBaseDataDir(), "bin")

    /**
     * 定位 jpackage 产出的 termora-cli 可执行（与主程序 launcher 同目录）。
     * 安装版才有；开发版（appPath 空）返回 null。
     */
    fun packagedLauncher(): File? {
        val appPath = Application.getAppPath()
        if (appPath.isBlank()) return null
        val dir = File(appPath).parentFile ?: return null
        val name = if (SystemInfo.isWindows) "$CLI_NAME.exe" else CLI_NAME
        val launcher = File(dir, name)
        return if (launcher.isFile) launcher else null
    }

    /** 生成 wrapper 到 dir，返回 wrapper 文件 */
    fun generateWrapper(dir: File): File {
        dir.mkdirs()
        val javaHome = System.getProperty("java.home")
        val classpath = System.getProperty("java.class.path")
        return if (SystemInfo.isWindows) {
            val javaExe = File(javaHome, "bin\\java.exe").absolutePath
            val f = File(dir, "$CLI_NAME.cmd")
            f.writeText("@echo off\r\n\"$javaExe\" -cp \"$classpath\" $MAIN_CLASS %*\r\n", Charsets.UTF_8)
            f
        } else {
            val javaExe = File(javaHome, "bin/java").absolutePath
            val f = File(dir, CLI_NAME)
            f.writeText("#!/bin/sh\nexec \"$javaExe\" -cp \"$classpath\" $MAIN_CLASS \"\$@\"\n", Charsets.UTF_8)
            runCatching {
                Files.setPosixFilePermissions(
                    f.toPath(),
                    setOf(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE,
                    )
                )
            }
            f
        }
    }

    /** 安装到 PATH，返回提示给用户的目标位置描述 */
    fun install(): String {
        val launcher = packagedLauncher()
        // 安装版：直接用官方 launcher 所在目录，无需自生成 wrapper
        val target = launcher ?: generateWrapper(binDir())
        return if (SystemInfo.isWindows) {
            addToWindowsUserPath(target.parentFile.absolutePath)
            target.parentFile.absolutePath
        } else {
            symlinkToLocalBin(target).absolutePath
        }
    }

    /** 从 PATH 移除（对称回退） */
    fun uninstall() {
        if (SystemInfo.isWindows) {
            removeFromWindowsUserPath(binDir().absolutePath)
        } else {
            val link = File(SystemUtils.getUserHome(), ".local/bin/$CLI_NAME")
            runCatching { Files.deleteIfExists(link.toPath()) }
        }
    }

    // ---- Windows ----
    private fun readUserPath(): String {
        return runCatching {
            if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, "Environment", "Path"))
                Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, "Environment", "Path")
            else ""
        }.getOrDefault("")
    }

    private fun addToWindowsUserPath(dir: String) {
        val parts = readUserPath().split(";").filter { it.isNotBlank() }
        if (parts.any { it.equals(dir, ignoreCase = true) }) return // 已存在，去重
        val newPath = (parts + dir).joinToString(";")
        Advapi32Util.registrySetExpandableStringValue(WinReg.HKEY_CURRENT_USER, "Environment", "Path", newPath)
    }

    private fun removeFromWindowsUserPath(dir: String) {
        val parts = readUserPath().split(";").filter { it.isNotBlank() && !it.equals(dir, ignoreCase = true) }
        Advapi32Util.registrySetExpandableStringValue(WinReg.HKEY_CURRENT_USER, "Environment", "Path", parts.joinToString(";"))
    }

    // ---- macOS / Linux ----
    private fun symlinkToLocalBin(wrapper: File): File {
        val localBin = File(SystemUtils.getUserHome(), ".local/bin")
        localBin.mkdirs()
        val link = File(localBin, CLI_NAME)
        Files.deleteIfExists(link.toPath())
        return runCatching {
            Files.createSymbolicLink(link.toPath(), wrapper.toPath()).toFile()
        }.getOrElse {
            // 某些文件系统不支持软链 → 复制 wrapper 内容
            wrapper.copyTo(link, overwrite = true)
            link
        }
    }
}
