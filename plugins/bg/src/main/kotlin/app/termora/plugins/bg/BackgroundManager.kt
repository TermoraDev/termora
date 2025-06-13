package app.termora.plugins.bg

import app.termora.*
import app.termora.database.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.File
import java.lang.ref.WeakReference
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

internal class BackgroundManager private constructor() : Disposable, GlassPaneAwareExtension,
    ApplicationRunnerExtension {
    companion object {
        private val log = LoggerFactory.getLogger(BackgroundManager::class.java)
        fun getInstance(): BackgroundManager {
            return ApplicationScope.Companion.forApplicationScope()
                .getOrCreate(BackgroundManager::class) { BackgroundManager() }
        }
    }

    private var bufferedImage: BufferedImage? = null
    private var imageFilepath = StringUtils.EMPTY
    private val glassPanes = mutableListOf<WeakReference<JComponent>>()


    fun setBackgroundImage(url: String) {
        clearBackgroundImage()
        Appearance.backgroundImage = url
        refreshBackgroundImage()
    }

    fun getBackgroundImage(): BufferedImage? {
        val bg = doGetBackgroundImage()
        if (bg == null) {
            if (JPopupMenu.getDefaultLightWeightPopupEnabled()) {
                return null
            } else {
                JPopupMenu.setDefaultLightWeightPopupEnabled(true)
            }
        } else {
            if (JPopupMenu.getDefaultLightWeightPopupEnabled()) {
                JPopupMenu.setDefaultLightWeightPopupEnabled(false)
            }
        }
        return bg
    }

    private fun doGetBackgroundImage(): BufferedImage? {
        synchronized(this) {
            return bufferedImage
        }
    }

    fun clearBackgroundImage() {
        synchronized(this) {
            bufferedImage = null
            imageFilepath = StringUtils.EMPTY
            Appearance.backgroundImage = StringUtils.EMPTY
        }
        refreshGlassPanes()
    }

    private fun refreshBackgroundImage() {
        val backgroundImage = Appearance.backgroundImage
        if (backgroundImage.isBlank()) {
            return
        }

        var file: File? = null

        // 从网络下载
        if (backgroundImage.startsWith("http://") || backgroundImage.startsWith("https://")) {
            file = Application.httpClient.newCall(
                Request.Builder().get()
                    .url(backgroundImage).build()
            ).execute().use { response ->
                val tempFile = File(Application.getTemporaryDir(), randomUUID())
                if (response.isSuccessful.not()) {
                    if (log.isErrorEnabled) {
                        log.error("Request {} failed with code {}", backgroundImage, response.code)
                    }
                    return
                }
                val body = response.body
                if (body != null) {
                    tempFile.outputStream().use { IOUtils.copy(body.byteStream(), it) }
                }
                IOUtils.closeQuietly(body)
                return@use tempFile
            }
        }

        val backgroundImageFile = File(backgroundImage)
        if (backgroundImageFile.isDirectory) {
            val files = FileUtils.listFiles(backgroundImageFile, arrayOf("png", "jpg", "jpeg"), false)
            if (files.isNotEmpty()) {
                for (i in 0 until files.size) {
                    file = files.randomOrNull()
                    if (file == null) break
                    if (file.absolutePath == imageFilepath) continue
                }
            } else {
                synchronized(this) {
                    imageFilepath = StringUtils.EMPTY
                    bufferedImage = null
                    refreshGlassPanes()
                }
            }
        } else if (backgroundImageFile.isFile) {
            file = backgroundImageFile
        }

        if (file == null || imageFilepath == file.absolutePath) {
            return
        }

        bufferedImage = file.inputStream().use { ImageIO.read(it) }
        imageFilepath = file.absolutePath

        refreshGlassPanes()
    }

    private fun refreshGlassPanes() {
        SwingUtilities.invokeLater {
            glassPanes.removeIf {
                val glassPane = it.get()
                glassPane?.repaint()
                glassPane == null
            }
        }
    }

    override fun dispose() {

    }

    override fun setGlassPane(window: Window, glassPane: JComponent) {
        glassPanes.add(WeakReference(glassPane))
    }

    override fun ready() {
        swingCoroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { refreshBackgroundImage() }.onFailure {
                    if (log.isErrorEnabled) {
                        log.error("Refresh failed", it)
                    }
                }
                delay(max(Appearance.interval, 30).seconds)
            }
        }
    }
}