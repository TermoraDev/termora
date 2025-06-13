package app.termora

import com.formdev.flatlaf.FlatSystemProperties
import com.formdev.flatlaf.util.SystemInfo
import com.pty4j.util.PtyUtil
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.slf4j.LoggerFactory
import org.tinylog.configuration.Configuration
import java.io.File
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

class ApplicationInitializr {

    fun run() {

        // 由于 macOS 签名和公证问题，依赖二进制依赖会单独在一个文件夹
        if (SystemUtils.IS_OS_MAC_OSX) {
            setupNativeLibraries()
        }

        if (SystemUtils.IS_OS_MAC_OSX) {
            System.setProperty("apple.awt.application.name", Application.getName())
        }

        // 设置 tinylog
        setupTinylog()

        // 检查是否单例
        checkSingleton()

        // 启动
        val runtime = measureTimeMillis { ApplicationRunner().run() }
        val log = LoggerFactory.getLogger(javaClass)
        if (log.isInfoEnabled) {
            log.info("Application initialization ${runtime}ms")
        }

    }


    private fun setupNativeLibraries() {
        if (!SystemUtils.IS_OS_MAC_OSX) {
            return
        }

        val appPath = Application.getAppPath()
        if (StringUtils.isBlank(appPath)) {
            return
        }

        val contents = File(appPath).parentFile?.parentFile ?: return
        val dylib = FileUtils.getFile(contents, "app", "dylib")
        if (!dylib.exists()) {
            return
        }

        val jna = FileUtils.getFile(dylib, "jna")
        if (jna.exists()) {
            System.setProperty("jna.boot.library.path", jna.absolutePath)
        }

        val pty4j = FileUtils.getFile(dylib, "pty4j")
        if (pty4j.exists()) {
            System.setProperty(PtyUtil.PREFERRED_NATIVE_FOLDER_KEY, pty4j.absolutePath)
        }

        val jSerialComm = FileUtils.getFile(dylib, "jSerialComm")
        if (jSerialComm.exists()) {
            System.setProperty("jSerialComm.library.path", jSerialComm.absolutePath)
        }

        val restart4j = FileUtils.getFile(dylib, "restart4j", "restarter")
        if (restart4j.exists()) {
            System.setProperty("restarter.path", restart4j.absolutePath)
        }

        val sqlite = FileUtils.getFile(dylib, "sqlite-jdbc")
        if (sqlite.exists()) {
            System.setProperty("org.sqlite.lib.path", sqlite.absolutePath)
        }

        val flatlaf = FileUtils.getFile(dylib, "flatlaf")
        if (flatlaf.exists()) {
            System.setProperty(FlatSystemProperties.NATIVE_LIBRARY_PATH, flatlaf.absolutePath)
        }

    }

    /**
     * Windows 情况覆盖
     */
    private fun setupTinylog() {
        if (SystemInfo.isWindows) {
            val dir = File(Application.getBaseDataDir(), "logs")
            FileUtils.forceMkdir(dir)
            Configuration.set("writer_file.latest", "${dir.absolutePath}/${Application.getName().lowercase()}.log")
            Configuration.set("writer_file.file", "${dir.absolutePath}/{date:yyyy}-{date:MM}-{date:dd}.log")
        }
    }

    private fun checkSingleton() {
        if (ApplicationSingleton.getInstance().isSingleton()) return
        System.err.println("Program is already running")
        exitProcess(1)
    }
}