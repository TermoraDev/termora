package app.termora

import app.termora.actions.ActionManager
import app.termora.database.DatabaseManager
import app.termora.keymap.KeymapManager
import app.termora.plugin.ExtensionManager
import app.termora.plugin.PluginManager
import app.termora.protocol.ProtocolProvider
import app.termora.protocol.TransferProtocolProvider
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.FlatSystemProperties
import com.formdev.flatlaf.extras.FlatDesktop
import com.formdev.flatlaf.extras.FlatInspector
import com.formdev.flatlaf.ui.FlatTableCellBorder
import com.formdev.flatlaf.util.SystemInfo
import com.jthemedetecor.OsThemeDetector
import com.mixpanel.mixpanelapi.ClientDelivery
import com.mixpanel.mixpanelapi.MessageBuilder
import com.mixpanel.mixpanelapi.MixpanelAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.LocaleUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.vfs2.VFS
import org.apache.commons.vfs2.cache.WeakRefFilesCache
import org.apache.commons.vfs2.impl.DefaultFileSystemManager
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.desktop.AppReopenedEvent
import java.awt.desktop.AppReopenedListener
import java.awt.desktop.SystemEventListener
import java.awt.event.ActionEvent
import java.awt.event.WindowEvent
import java.util.*
import java.util.concurrent.CountDownLatch
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.system.exitProcess

class ApplicationRunner {
    private val log by lazy { LoggerFactory.getLogger(ApplicationRunner::class.java) }

    fun run() {

        // 异步初始化
        val loadPluginThread = Thread.ofVirtual().start { PluginManager.getInstance() }

        // 打印系统信息
        printSystemInfo()

        // 打开数据库
        openDatabase()

        // 加载设置
        loadSettings()

        // 统计
        enableAnalytics()

        // init ActionManager、KeymapManager、VFS
        swingCoroutineScope.launch(Dispatchers.IO) {
            ActionManager.getInstance()
            KeymapManager.getInstance()
        }

        // 设置 LAF
        setupLaf()

        // clear temporary
        clearTemporary()

        // 等待插件加载完成
        loadPluginThread.join()

        // 初始化 VFS
        val fileSystemManager = DefaultFileSystemManager()
        for (provider in ProtocolProvider.providers.filterIsInstance<TransferProtocolProvider>()) {
            fileSystemManager.addProvider(provider.getProtocol().lowercase(), provider.getFileProvider())
        }
        fileSystemManager.filesCache = WeakRefFilesCache()
        fileSystemManager.init()
        VFS.setManager(fileSystemManager)

        // 准备就绪
        for (extension in ExtensionManager.getInstance().getExtensions(ApplicationRunnerExtension::class.java)) {
            extension.ready()
        }

        // 启动主窗口
        SwingUtilities.invokeLater { startMainFrame() }

    }

    private fun clearTemporary() {
        swingCoroutineScope.launch(Dispatchers.IO) {
            // 启动时清除
            FileUtils.cleanDirectory(Application.getTemporaryDir())
        }

    }

    private fun startMainFrame() {


        TermoraFrameManager.getInstance().createWindow().isVisible = true

        if (SystemInfo.isMacOS) {
            SwingUtilities.invokeLater {

                try {
                    // 设置 Dock
                    setupMacOSDock()
                } catch (e: Exception) {
                    if (log.isWarnEnabled) {
                        log.warn(e.message, e)
                    }
                }

                // Command + Q
                FlatDesktop.setQuitHandler { quitHandler() }
            }
        } else if (SystemInfo.isWindows) {
            // 设置托盘
            SwingUtilities.invokeLater { setupSystemTray() }
        }

        // 初始化 Scheme
        OpenURIHandlers.getInstance()
    }

    private fun setupSystemTray() {
        if (!SystemInfo.isWindows || !SystemTray.isSupported()) return

        val tray = SystemTray.getSystemTray()
        val image = ImageIO.read(TermoraFrame::class.java.getResourceAsStream("/icons/termora_16x16.png"))
        val trayIcon = TrayIcon(image)
        val popupMenu = PopupMenu()
        trayIcon.popupMenu = popupMenu
        trayIcon.toolTip = Application.getName()

        // PopupMenu 不支持中文
        val exitMenu = MenuItem("Exit")
        exitMenu.addActionListener { SwingUtilities.invokeLater { quitHandler() } }
        popupMenu.add(exitMenu)

        // double click
        trayIcon.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                TermoraFrameManager.getInstance().tick()
            }
        })

        tray.add(trayIcon)

        Disposer.register(ApplicationScope.forApplicationScope(), object : Disposable {
            override fun dispose() {
                tray.remove(trayIcon)
            }
        })
    }

    private fun quitHandler() {
        val windows = TermoraFrameManager.getInstance().getWindows()

        for (frame in windows) {
            frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSED))
        }

        Disposer.dispose(TermoraFrameManager.getInstance())
    }

    private fun loadSettings() {
        val language = DatabaseManager.getInstance().appearance.language
        val locale = runCatching { LocaleUtils.toLocale(language) }.getOrElse { Locale.getDefault() }
        if (log.isInfoEnabled) {
            log.info("Language: {} , Locale: {}", language, locale)
        }
        Locale.setDefault(locale)
    }


    private fun setupLaf() {

        System.setProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS, "${SystemInfo.isLinux || SystemInfo.isWindows}")
        System.setProperty(FlatSystemProperties.USE_ROUNDED_POPUP_BORDER, "false")

        if (SystemInfo.isLinux) {
            JFrame.setDefaultLookAndFeelDecorated(true)
            JDialog.setDefaultLookAndFeelDecorated(true)
        }

        val themeManager = ThemeManager.getInstance()
        val appearance = DatabaseManager.getInstance().appearance
        var theme = appearance.theme
        // 如果是跟随系统
        if (appearance.followSystem) {
            theme = if (OsThemeDetector.getDetector().isDark) {
                appearance.darkTheme
            } else {
                appearance.lightTheme
            }
        }

        themeManager.change(theme, true)


        if (Application.isUnknownVersion())
            FlatInspector.install("ctrl shift alt X")

        UIManager.put(FlatClientProperties.FULL_WINDOW_CONTENT, true)
        UIManager.put(FlatClientProperties.USE_WINDOW_DECORATIONS, false)
        UIManager.put("TitlePane.useWindowDecorations", false)

        UIManager.put("Component.arc", 5)
        UIManager.put("TextComponent.arc", UIManager.getInt("Component.arc"))
        UIManager.put("Component.hideMnemonics", false)

        UIManager.put("TitleBar.height", 36)

        UIManager.put("Dialog.width", 650)
        UIManager.put("Dialog.height", 550)


        if (SystemInfo.isMacOS) {
            UIManager.put("TabbedPane.tabHeight", UIManager.getInt("TitleBar.height"))
        } else if (SystemInfo.isLinux) {
            UIManager.put("TabbedPane.tabHeight", UIManager.getInt("TitleBar.height") - 4)
        } else {
            UIManager.put("TabbedPane.tabHeight", UIManager.getInt("TitleBar.height") - 6)
        }

        if (SystemInfo.isLinux) {
            UIManager.put("TitlePane.centerTitle", true)
            UIManager.put("TitlePane.showIcon", false)
            UIManager.put("TitlePane.showIconInDialogs", false)
        }

        UIManager.put("Table.rowHeight", 24)
        UIManager.put("Table.focusCellHighlightBorder", FlatTableCellBorder.Default())
        UIManager.put("Table.focusSelectedCellHighlightBorder", FlatTableCellBorder.Default())
        UIManager.put("Table.selectionArc", UIManager.getInt("Component.arc"))

        UIManager.put("Tree.rowHeight", 24)
        UIManager.put("Tree.background", DynamicColor("window"))
        UIManager.put("Tree.selectionArc", UIManager.getInt("Component.arc"))
        UIManager.put("Tree.showCellFocusIndicator", false)
        UIManager.put("Tree.repaintWholeRow", true)

        UIManager.put("List.selectionArc", UIManager.getInt("Component.arc"))

    }

    private fun setupMacOSDock() {
        val countDownLatch = CountDownLatch(1)
        val cls = Class.forName("com.apple.eawt.Application")
        val app = cls.getMethod("getApplication").invoke(null)
        val addAppEventListener = cls.getMethod("addAppEventListener", SystemEventListener::class.java)

        addAppEventListener.invoke(app, object : AppReopenedListener {
            override fun appReopened(e: AppReopenedEvent) {
                val manager = TermoraFrameManager.getInstance()
                if (manager.getWindows().isEmpty()) {
                    manager.createWindow().isVisible = true
                }
            }
        })

        // 当应用程序销毁时，驻守线程也可以退出了
        Disposer.register(ApplicationScope.forApplicationScope(), object : Disposable {
            override fun dispose() {
                countDownLatch.countDown()
            }
        })

        // 驻守线程，不然当所有窗口都关闭时，程序会自动退出
        // wait application exit
        Thread.ofPlatform().daemon(false)
            .priority(Thread.MIN_PRIORITY)
            .start { countDownLatch.await() }
    }

    private fun printSystemInfo() {
        if (log.isDebugEnabled) {
            log.debug("Welcome to ${Application.getName()} ${Application.getVersion()}!")
            log.debug(
                "JVM name: {} , vendor: {} , version: {}",
                SystemUtils.JAVA_VM_NAME,
                SystemUtils.JAVA_VM_VENDOR,
                SystemUtils.JAVA_VM_VERSION,
            )
            log.debug(
                "OS name: {} , version: {} , arch: {}",
                SystemUtils.OS_NAME,
                SystemUtils.OS_VERSION,
                SystemUtils.OS_ARCH
            )
            log.debug("Base config dir: ${Application.getBaseDataDir().absolutePath}")
        }
    }


    private fun openDatabase() {
        try {
            // 初始化数据库
            DatabaseManager.getInstance()
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
            JOptionPane.showMessageDialog(
                null, "Unable to open database",
                I18n.getString("termora.title"), JOptionPane.ERROR_MESSAGE
            )
            exitProcess(1)
        }
    }

    /**
     * 统计 https://mixpanel.com
     */
    private fun enableAnalytics() {
        if (Application.isUnknownVersion()) {
            return
        }

        swingCoroutineScope.launch(Dispatchers.IO) {
            try {
                val properties = JSONObject()
                properties.put("os", SystemUtils.OS_NAME)
                if (SystemInfo.isLinux) {
                    properties.put("platform", "Linux")
                } else if (SystemInfo.isWindows) {
                    properties.put("platform", "Windows")
                } else if (SystemInfo.isMacOS) {
                    properties.put("platform", "macOS")
                }
                properties.put("version", Application.getVersion())
                properties.put("language", Locale.getDefault().toString())
                val message = MessageBuilder("0871335f59ee6d0eb246b008a20f9d1c")
                    .event(getAnalyticsUserID(), "launch", properties)
                val delivery = ClientDelivery()
                delivery.addMessage(message)
                MixpanelAPI().deliver(delivery, true)
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            }
        }
    }

    private fun getAnalyticsUserID(): String {
        val properties = DatabaseManager.getInstance().properties
        var id = properties.getString("AnalyticsUserID")
        if (id.isNullOrBlank()) {
            id = randomUUID()
            properties.putString("AnalyticsUserID", id)
        }
        return id
    }

}