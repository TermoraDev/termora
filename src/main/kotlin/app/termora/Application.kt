package app.termora

import com.formdev.flatlaf.util.SystemInfo
import com.jthemedetecor.util.OsInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.time.Duration
import java.util.*
import kotlin.math.ln
import kotlin.math.pow
import kotlin.reflect.KClass


object Application {
    private val services = Collections.synchronizedMap(mutableMapOf<KClass<*>, Any>())
    private lateinit var baseDataDir: File


    val ohMyJson = Json {
        ignoreUnknownKeys = true
        // 默认值不输出
        encodeDefaults = false
    }


    val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .callTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(60))
            .readTimeout(Duration.ofSeconds(60))
            .addInterceptor(
                HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
                    private val log = LoggerFactory.getLogger(HttpLoggingInterceptor::class.java)
                    override fun log(message: String) {
                        if (log.isDebugEnabled) log.debug(message)
                    }
                }).setLevel(HttpLoggingInterceptor.Level.BASIC)
            )
            .build()
    }

    fun getDefaultShell(): String {
        if (SystemInfo.isWindows) {
            return "cmd.exe"
        } else {
            val shell = System.getenv("SHELL")
            if (shell != null && shell.isNotBlank()) {
                return shell
            }
        }
        return "/bin/bash"
    }

    fun getBaseDataDir(): File {
        if (::baseDataDir.isInitialized) {
            return baseDataDir
        }

        // 从启动参数取
        var baseDataDir = System.getProperty("${getName()}.base-data-dir".lowercase())
        // 取不到从环境取
        if (StringUtils.isBlank(baseDataDir)) {
            baseDataDir = System.getenv("${getName()}_BASE_DATA_DIR".uppercase())
        }

        var dir = File(SystemUtils.getUserHome(), ".${getName()}".lowercase())
        if (StringUtils.isNotBlank(baseDataDir)) {
            dir = File(baseDataDir)
        }


        FileUtils.forceMkdir(dir)
        Application.baseDataDir = dir

        return dir
    }

    fun getDatabaseFile(): File {
        return FileUtils.getFile(getBaseDataDir(), "storage")
    }

    fun getVersion(): String {
        var version = System.getProperty("jpackage.app-version")
        if (version.isNullOrBlank()) {
            version = System.getProperty("app-version")
        }
        if (version.isNullOrBlank()) {
            version = "unknown"
        }
        return version
    }

    fun isUnknownVersion(): Boolean {
        return getVersion().contains("unknown")
    }

    fun getAppPath(): String {
        return StringUtils.defaultString(System.getProperty("jpackage.app-path"))
    }

    fun getName(): String {
        return "Termora"
    }

    fun browse(uri: URI, async: Boolean = true) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri)
        } else if (async) {
            @Suppress("OPT_IN_USAGE")
            GlobalScope.launch(Dispatchers.IO) { tryBrowse(uri) }
        } else {
            tryBrowse(uri)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getService(clazz: KClass<T>): T {
        if (services.containsKey(clazz)) {
            return services[clazz] as T
        }
        throw IllegalStateException("$clazz does not exist")
    }

    @Synchronized
    fun registerService(clazz: KClass<*>, service: Any) {
        if (services.containsKey(clazz)) {
            throw IllegalStateException("$clazz already registered")
        }
        services[clazz] = service
    }

    private fun tryBrowse(uri: URI) {
        if (SystemInfo.isWindows) {
            ProcessBuilder("explorer", uri.toString()).start()
        } else if (SystemInfo.isMacOS) {
            ProcessBuilder("open", uri.toString()).start()
        } else if (SystemInfo.isLinux && OsInfo.isGnome()) {
            ProcessBuilder("xdg-open", uri.toString()).start()
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB")
    val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
    val value = bytes / 1024.0.pow(exp.toDouble())

    return String.format("%.2f %s", value, units[exp])
}

fun formatSeconds(seconds: Long): String {
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60

    return when {
        days > 0 -> "${days}天${hours}小时${minutes}分${remainingSeconds}秒"
        hours > 0 -> "${hours}小时${minutes}分${remainingSeconds}秒"
        minutes > 0 -> "${minutes}分${remainingSeconds}秒"
        else -> "${remainingSeconds}秒"
    }
}

