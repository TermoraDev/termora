package app.termora.plugin.internal.plugin

import app.termora.*
import app.termora.plugin.ExtensionManager
import app.termora.plugin.PluginManager
import app.termora.plugin.PluginOrigin
import app.termora.plugin.PluginUninstallExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.net.io.CopyStreamAdapter
import org.apache.commons.net.io.CopyStreamListener
import org.apache.commons.net.io.Util
import org.jdesktop.swingx.JXLabel
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.swing.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.round


class PluginPanel(val descriptor: PluginPluginDescriptor) : JPanel(), Disposable {
    companion object {
        private val log = LoggerFactory.getLogger(PluginPanel::class.java)
        private val installed = mutableSetOf<String>()
        private val uninstalled = mutableSetOf<String>()
        private val publicKey = Ed25519.generatePublic(
            Base64.decodeBase64("MCowBQYDK2VwAyEAHPyJ5kt2UHWYUPnWU84DOEhCCUE5FEpzdAbeTCNV31A")
        )
    }

    private val restartButton = JButton(I18n.getString("termora.settings.restart.title"))
    private val updateButton = InstallButton()
    private val installButton = InstallButton()
    private val uninstallButton = JButton(I18n.getString("termora.settings.plugin.uninstall"))

    private val restarter get() = TermoraRestarter.getInstance()
    private val pluginManager get() = PluginManager.getInstance()
    private val owner get() = SwingUtilities.getWindowAncestor(this)

    init {
        initView()
        initEvents()
    }

    private fun initView() {
        this.setLayout(BoxLayout(this, BoxLayout.X_AXIS))
        this.add(JLabel(descriptor.icon))
        this.add(Box.createHorizontalStrut(8))

        val infoBox = Box.createVerticalBox()
        infoBox.add(JLabel("<html><b>${descriptor.plugin.getName()}</b>&nbsp;&nbsp;${descriptor.version}</html>"))
        infoBox.add(Box.createVerticalStrut(4))
        val descriptionLabel = JXLabel(descriptor.description)
            .apply { foreground = DynamicColor("textInactiveText") }
        descriptionLabel.preferredSize = Dimension(0, descriptionLabel.preferredSize.height)
        descriptionLabel.toolTipText = descriptor.description

        infoBox.add(descriptionLabel)
        this.add(infoBox)
        this.add(Box.createHorizontalGlue())
        this.add(Box.createHorizontalStrut(8))

        this.add(Box.createHorizontalGlue())

        installButton.text = I18n.getString("termora.settings.plugin.install")
        updateButton.text = I18n.getString("termora.update.update")

        installButton.isFocusable = false
        restartButton.isFocusable = false
        updateButton.isFocusable = false
        uninstallButton.isFocusable = false


        // 刷新按钮
        refreshButtons()


        this.add(updateButton)
        this.add(restartButton)
        this.add(installButton)
        this.add(uninstallButton)

        this.border = BorderFactory.createEmptyBorder(0, 8, 0, 8)


    }

    fun refreshButtons() {

        restartButton.isVisible = false
        installButton.isVisible = false
        uninstallButton.isVisible = false
        updateButton.isVisible = false

        // 系统级别的插件不显示任何按钮
        if (descriptor.origin == PluginOrigin.System || descriptor.origin == PluginOrigin.Internal) return

        if (PluginOption.installedFromDisk.any { it.id == descriptor.id }
            || installed.contains(descriptor.id) || uninstalled.contains(descriptor.id)
            || descriptor.origin == PluginOrigin.Memory
        ) {
            restartButton.isVisible = true
        } else if (descriptor.marketplace) {
            val loadedPlugin = pluginManager.getLoadedPluginDescriptor().firstOrNull { it.id == descriptor.id }
            if (loadedPlugin != null) {
                val localVersion = loadedPlugin.version
                val remoteVersion = descriptor.version
                if (localVersion >= remoteVersion) {
                    installButton.isVisible = true
                    installButton.text = I18n.getString("termora.settings.plugin.installed")
                    installButton.isEnabled = false
                } else {
                    updateButton.isVisible = true
                }
            } else {
                installButton.isVisible = true
            }
        } else {
            uninstallButton.isVisible = true
        }
    }

    private fun initEvents() {

        restartButton.addActionListener {
            if (restarter.isSupported) {
                restarter.scheduleRestart(owner)
            } else {
                OptionPane.showMessageDialog(
                    owner, I18n.getString("termora.settings.restart.manually"),
                    messageType = JOptionPane.WARNING_MESSAGE
                )
            }
        }

        uninstallButton.addActionListener { uninstall() }

        updateButton.addActionListener { update() }

        installButton.addActionListener { install() }

    }

    private fun install() {
        installOrUpdate(installButton)
    }

    private fun uninstall() {
        if (descriptor.origin != PluginOrigin.External) return
        val path = descriptor.path ?: return
        if (path.exists().not() || path.isDirectory.not()) return

        if (OptionPane.showConfirmDialog(
                owner, I18n.getString("termora.settings.plugin.uninstall-confirm", descriptor.plugin.getName()),
                optionType = JOptionPane.OK_CANCEL_OPTION,
                options = arrayOf(
                    I18n.getString("termora.settings.plugin.uninstall"),
                    I18n.getString("termora.cancel")
                ),
                initialValue = I18n.getString("termora.settings.plugin.uninstall")
            ) != JOptionPane.OK_OPTION
        ) return

        if (FileUtils.getFile(path, "uninstalled").createNewFile()) {
            uninstallButton.isVisible = false
            restartButton.isVisible = true

            uninstalled.add(descriptor.id)

            try {
                ExtensionManager.getInstance().getExtensions(PluginUninstallExtension::class.java)
                    .forEach { it.uninstalled() }
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            }

            // 询问是否重启
            TermoraRestarter.getInstance().scheduleRestart(owner)
        } else {
            OptionPane.showMessageDialog(
                owner, I18n.getString("termora.settings.plugin.uninstall-failed"),
                messageType = JOptionPane.ERROR_MESSAGE
            )
        }
    }

    private fun update() {
        installOrUpdate(updateButton)
    }

    private fun installOrUpdate(button: InstallButton) {


        button.installing = true
        button.progress = 0
        button.isEnabled = false


        val job = swingCoroutineScope.launch(Dispatchers.IO) {
            try {
                downloadPlugin(object : CopyStreamAdapter() {
                    override fun bytesTransferred(
                        totalBytesTransferred: Long,
                        bytesTransferred: Int,
                        streamSize: Long
                    ) {
                        if (!isActive) throw CancellationException()
                        val oldProgress = button.progress
                        val progress = round(totalBytesTransferred * 1.0 / streamSize * 100.0).toInt()
                        if (oldProgress != progress) {
                            SwingUtilities.invokeLater {
                                button.progress = progress
                                button.repaint()
                            }
                        }
                    }
                }, button == updateButton)

                withContext(Dispatchers.Swing) {
                    installed.add(descriptor.id)
                    restarter.scheduleRestart(owner)

                    // 如果是更新，那么也需要刷新 InstalledPanel 下的按钮状态
                    if (button == updateButton) {
                        val pluginOption = SwingUtilities.getAncestorOfClass(PluginOption::class.java, button)
                        if (pluginOption != null) {
                            for (panel in SwingUtils.getDescendantsOfType(PluginPanel::class.java, pluginOption)) {
                                if (panel.descriptor.id == descriptor.id) {
                                    panel.refreshButtons()
                                }
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Swing) { button.isEnabled = true }
                if (e !is UserCancelException) {
                    if (log.isErrorEnabled) {
                        log.error(e.message, e)
                    }
                    withContext(Dispatchers.Swing) {
                        button.isEnabled = true
                        OptionPane.showMessageDialog(
                            owner,
                            I18n.getString("termora.settings.plugin.install-failed"),
                            messageType = JOptionPane.ERROR_MESSAGE,
                        )
                    }
                }
            } finally {
                withContext(Dispatchers.Swing) {
                    button.installing = false
                    refreshButtons()
                }
            }
        }

        Disposer.register(this, object : Disposable {
            override fun dispose() {
                if (job.isActive)
                    job.cancel()
            }
        })
    }

    private suspend fun downloadPlugin(listener: CopyStreamListener, updated: Boolean) {

        val request = Request.Builder()
            .url(descriptor.downloadUrl)
            .get()
            .build()
        val response = Application.httpClient.newCall(request).execute()
        if (response.isSuccessful.not()) {
            IOUtils.closeQuietly(response)
            if (log.isErrorEnabled) {
                log.error(
                    "Plugin {} download failed, url: {}, status: {}",
                    descriptor.id,
                    descriptor.downloadUrl,
                    response.code
                )
            }
            throw ResponseException(response.code, response)
        }

        val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1

        val tempFile = FileUtils.getFile(Application.getTemporaryDir(), randomUUID())

        tempFile.outputStream().use { fos ->
            response.use { resp ->
                val body = resp.body ?: throw ResponseException(response.code, resp)
                body.byteStream().use { input ->
                    Util.copyStream(
                        input,
                        fos,
                        Util.DEFAULT_COPY_BUFFER_SIZE,
                        contentLength, listener
                    )
                }
            }
        }

        // 验证签名
        val signature = tempFile.inputStream()
            .use { Ed25519.verify(publicKey, it, Base64.decodeBase64(descriptor.signature)) }

        // 如果签名认证失败，那么提示用户有风险
        if (signature.not()) {
            val stillInstall = withContext(Dispatchers.Swing) {
                OptionPane.showConfirmDialog(
                    owner,
                    I18n.getString(
                        "termora.settings.plugin.install-from-disk-warning",
                        descriptor.plugin.getName()
                    ),
                    optionType = JOptionPane.OK_CANCEL_OPTION,
                    messageType = JOptionPane.WARNING_MESSAGE,
                    options = arrayOf(
                        I18n.getString("termora.settings.plugin.install"),
                        I18n.getString("termora.cancel")
                    ),
                    initialValue = I18n.getString("termora.settings.plugin.install")
                ) == JOptionPane.OK_OPTION
            }
            if (stillInstall.not()) throw UserCancelException()
        }


        var pluginDirectory = FileUtils.getFile(PluginManager.getInstance().getPluginDirectory(), descriptor.id)
        if (updated) {
            pluginDirectory = FileUtils.getFile(pluginDirectory, "updated")
        }
        if (pluginDirectory.exists()) {
            FileUtils.deleteQuietly(pluginDirectory)
        }
        FileUtils.forceMkdir(pluginDirectory)


        tempFile.inputStream().use { input ->
            ZipInputStream(input).use { zis ->
                var zipEntry: ZipEntry? = zis.getNextEntry()
                while (zipEntry != null) {
                    val file = FileUtils.getFile(pluginDirectory, zipEntry.name)
                    if (zipEntry.isDirectory) {
                        FileUtils.forceMkdir(file)
                    } else {
                        file.outputStream().use { output -> zis.copyTo(output) }
                    }
                    zipEntry = zis.getNextEntry()
                }
            }
        }

        FileUtils.deleteQuietly(tempFile)

    }

    private class UserCancelException : RuntimeException()

}