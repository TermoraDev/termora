package app.termora.plugins.geo

import app.termora.Application
import app.termora.ApplicationScope
import app.termora.Disposable
import com.maxmind.db.CHMCache
import com.maxmind.geoip2.DatabaseReader
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.net.InetAddress
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.jvm.optionals.getOrNull


internal class Geo private constructor() : Disposable {
    companion object {
        private val log = LoggerFactory.getLogger(Geo::class.java)

        fun getInstance(): Geo {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(Geo::class) { Geo() }
        }
    }

    private val initialized = AtomicBoolean(false)
    private var reader: DatabaseReader? = null

    private fun initialize() {
        if (GeoApplicationRunnerExtension.instance.isReady().not()) return
        if (isInitialized()) return

        if (initialized.compareAndSet(false, true)) {
            try {
                val database = getDatabaseFile()
                if ((database.exists() && database.isFile).not()) {
                    throw IllegalStateException("${database.absolutePath} not be found")
                }
                val locale = Locale.getDefault().toString().replace("_", "-")
                try {
                    reader = DatabaseReader.Builder(database)
                        .locales(listOf(locale, "en"))
                        .withCache(CHMCache()).build()
                } catch (e: Exception) {

                    // 打开数据失败一般都是数据文件顺坏，删除数据库
                    FileUtils.deleteQuietly(database)

                    // 重新下载
                    GeoApplicationRunnerExtension.instance.reload()

                    throw e
                }
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error("Failed to initialize geo database", e)
                }
                initialized.set(false)
            }
        }

    }

    fun getDatabaseFile(): File {
        val dir = FileUtils.getFile(Application.getBaseDataDir(), "config", "plugins", "geo")
        return File(dir, "GeoLite2-Country.mmdb")
    }

    fun country(ip: String): Country? {
        try {
            initialize()

            val reader = reader ?: return null
            val response = reader.tryCountry(InetAddress.getByName(ip)).getOrNull() ?: return null
            val isoCode = response.country.isoCode
            var name = response.country.name
            // 控制名称不要太长，如果太长则使用缩写。例如：United States
            if (name != null && name.length > 6) name = isoCode
            return Country(isoCode, name ?: isoCode)
        } catch (e: Exception) {
            if (log.isDebugEnabled) {
                log.error("Failed to initialize geo database", e)
            }
            return null
        }
    }

    fun isInitialized(): Boolean = initialized.get()

    override fun dispose() {
        IOUtils.closeQuietly(reader)
    }

    data class Country(val isoCode: String, val name: String)
}