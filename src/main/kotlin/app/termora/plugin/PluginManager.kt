package app.termora.plugin

import app.termora.Application
import app.termora.ApplicationScope
import app.termora.account.AccountPlugin
import app.termora.plugin.internal.badge.BadgePlugin
import app.termora.plugin.internal.extension.DynamicExtensionPlugin
import app.termora.plugin.internal.local.LocalInternalPlugin
import app.termora.plugin.internal.plugin.PluginInternalPlugin
import app.termora.plugin.internal.rdp.RDPInternalPlugin
import app.termora.plugin.internal.sftppty.SFTPPtyInternalPlugin
import app.termora.plugin.internal.ssh.SSHInternalPlugin
import app.termora.plugin.internal.telnet.TelnetInternalPlugin
import app.termora.plugin.internal.wsl.WSLInternalPlugin
import app.termora.swingCoroutineScope
import app.termora.terminal.panel.vw.FloatingToolbarPlugin
import app.termora.transfer.internal.local.LocalPlugin
import app.termora.transfer.internal.sftp.SFTPPlugin
import com.formdev.flatlaf.util.SystemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.filefilter.FileFilterUtils
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.semver4j.Semver
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.*

internal class PluginManager private constructor() {
    companion object {
        private val log = LoggerFactory.getLogger(PluginManager::class.java)
        private const val PLUGIN_DIR = "TERMORA_PLUGIN_DIRECTORY"

        fun getInstance(): PluginManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(PluginManager::class) { PluginManager() }
        }
    }

    private val plugins = mutableListOf<PluginDescriptor>()
    private val pluginIds = mutableSetOf<String>()

    init {
        // load internal plugins
        loadInternalPlugins()
        // load system plugins
        loadSystemPlugins()
        // load user plugins
        loadPlugins(getPluginDirectory(), PluginOrigin.External)
    }

    /**
     * 获取已经加载的插件
     */
    fun getLoadedPlugins(): List<Plugin> {
        return plugins.map { it.plugin }
    }

    fun getLoadedPluginDescriptor(): Array<PluginDescriptor> {
        return plugins.sortedBy { it.plugin.getName().length }.toTypedArray()
    }

    fun getPluginDirectory(): File {
        val dir = StringUtils.defaultIfBlank(
            System.getProperty(PLUGIN_DIR),
            System.getenv(PLUGIN_DIR)
        )
        if (StringUtils.isNotBlank(dir)) {
            return File(dir)
        }
        return File(Application.getBaseDataDir(), "plugins")
    }

    private fun loadPlugins(pluginsFile: File, origin: PluginOrigin) {
        if (log.isInfoEnabled) {
            log.info("Loading plugins ${FilenameUtils.normalize(pluginsFile.absolutePath)}")
        }

        if (pluginsFile.exists().not() || pluginsFile.isDirectory.not()) return
        val dirs = pluginsFile.listFiles { file -> file.isDirectory }
        if (ArrayUtils.isEmpty(dirs)) return

        for (file in dirs) {
            try {
                loadPlugin(file, origin)
            } catch (e: Throwable) {
                if (log.isErrorEnabled) {
                    log.error("Failed to load plugin file $file", e)
                }
            }
        }
    }

    private fun loadInternalPlugins() {
        val version = Semver.parse(Application.getVersion()) ?: return

        // 动态注册扩展
        plugins.add(PluginDescriptor(DynamicExtensionPlugin(), origin = PluginOrigin.Internal, version = version))
        // plugin
        plugins.add(PluginDescriptor(PluginInternalPlugin(), origin = PluginOrigin.Internal, version = version))
        // account plugin
        plugins.add(PluginDescriptor(AccountPlugin(), origin = PluginOrigin.Internal, version = version))
        // badge plugin
        plugins.add(PluginDescriptor(BadgePlugin(), origin = PluginOrigin.Internal, version = version))

        // ssh plugin
        plugins.add(PluginDescriptor(SSHInternalPlugin(), origin = PluginOrigin.Internal, version = version))
        // local plugin
        plugins.add(PluginDescriptor(LocalInternalPlugin(), origin = PluginOrigin.Internal, version = version))
        // rdp plugin
        plugins.add(PluginDescriptor(RDPInternalPlugin(), origin = PluginOrigin.Internal, version = version))
        // telnet plugin
        plugins.add(PluginDescriptor(TelnetInternalPlugin(), origin = PluginOrigin.Internal, version = version))
        // wsl plugin
        if (SystemUtils.IS_OS_WINDOWS) {
            plugins.add(PluginDescriptor(WSLInternalPlugin(), origin = PluginOrigin.Internal, version = version))
        }
        // sftp pty plugin
        plugins.add(PluginDescriptor(SFTPPtyInternalPlugin(), origin = PluginOrigin.Internal, version = version))

        // local transfer plugin
        plugins.add(PluginDescriptor(LocalPlugin(), origin = PluginOrigin.Internal, version = version))
        // sftp transfer plugin
        plugins.add(PluginDescriptor(SFTPPlugin(), origin = PluginOrigin.Internal, version = version))

        // floating
        plugins.add(PluginDescriptor(FloatingToolbarPlugin(), origin = PluginOrigin.Internal, version = version))
    }

    private fun loadSystemPlugins() {
        val appPath = Application.getAppPath()
        if (appPath.isBlank()) return

        val plugins = if (SystemInfo.isMacOS) {
            val contents = File(appPath).parentFile?.parentFile ?: return
            File(contents, "plugins")
        } else if (SystemInfo.isWindows) {
            val contents = File(appPath).parentFile ?: return
            FileUtils.getFile(contents, "plugins")
        } else if (SystemInfo.isLinux) {
            val contents = File(appPath).parentFile?.parentFile ?: return
            FileUtils.getFile(contents, "lib", "plugins")
        } else {
            throw IllegalStateException(SystemUtils.OS_NAME)
        }

        loadPlugins(plugins, PluginOrigin.System)
    }

    private fun loadPlugin(file: File, origin: PluginOrigin) {

        // 如果有卸载标识，那么忽略
        val uninstalledFile = FileUtils.getFile(file, "uninstalled")
        if (uninstalledFile.exists()) {
            swingCoroutineScope.launch(Dispatchers.IO) { FileUtils.deleteQuietly(file) }
            return
        }

        // 如果存在更新文件夹，那么需要更新
        val updatedFile = FileUtils.getFile(file, "updated")
        val updatedFiles = updatedFile.listFiles { it.isFile } ?: emptyArray()
        if (updatedFile.exists()) {
            if (updatedFiles.isNotEmpty()) {
                for (item in file.listFiles { it.isFile } ?: emptyArray()) {
                    FileUtils.deleteQuietly(item)
                }
                // 将更新文件夹的文件移动到插件目录
                for (item in updatedFiles) {
                    FileUtils.moveFileToDirectory(item, file, false)
                }
            }
            FileUtils.deleteQuietly(updatedFile)
        }

        val jars = FileUtils.listFiles(
            file, FileFilterUtils.suffixFileFilter(".jar"),
            FileFilterUtils.falseFileFilter()
        )
        if (jars.isEmpty()) return
        val loader = PluginClassLoader(jars.toTypedArray())

        for (e in loader.findResources("META-INF/plugin.xml")) {

            try {

                val iconResource = loader.findResource("META-INF/pluginIcon.svg")
                val darkIconResource = loader.findResource("META-INF/pluginIcon_dark.svg")

                val pluginDescriptor = PluginXmlParser.parse(
                    e.openStream(),
                    iconResource?.openStream(),
                    darkIconResource?.openStream()
                )

                if (pluginIds.contains(pluginDescriptor.id)) continue

                val clazz = Class.forName(pluginDescriptor.entry, false, loader)
                if (Plugin::class.java.isAssignableFrom(clazz).not()) continue
                val entry = clazz.getConstructor().newInstance() as Plugin

                pluginIds.add(pluginDescriptor.id)
                plugins.add(
                    PluginDescriptor(
                        entry,
                        icon = pluginDescriptor.icon,
                        origin = origin,
                        id = pluginDescriptor.id,
                        version = pluginDescriptor.version,
                        path = file,
                        descriptions = pluginDescriptor.descriptions
                    )
                )

                if (log.isInfoEnabled) {
                    log.info("Loaded plugin ${entry.getName()} from $entry")
                }


                break

            } catch (ex: Throwable) {
                if (log.isErrorEnabled) {
                    log.error("Failed to load plugin entry ${e.file}", ex)
                }
            }
        }

    }

    private class PluginClassLoader(jarFiles: Array<File>) :
        URLClassLoader(jarFiles.map { it.toURI().toURL() }.toTypedArray()) {

        override fun getResources(name: String?): Enumeration<URL?>? {
            return findResources(name)
        }

        override fun getResource(name: String?): URL? {
            return findResource(name)
        }

    }
}