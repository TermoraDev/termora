package app.termora

import app.termora.database.DatabaseManager
import app.termora.plugin.ExtensionManager
import app.termora.plugin.PluginManager
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
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.desktop.AppReopenedEvent
import java.awt.desktop.AppReopenedListener
import java.awt.desktop.SystemEventListener
import java.awt.event.*
import java.util.*
import java.util.concurrent.CountDownLatch
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
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

        // 设置 LAF
        setupLaf()

        // clear temporary
        clearTemporary()

        // 等待插件加载完成
        loadPluginThread.join()

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
        val image = ImageIO.read(TermoraFrame::class.java.getResourceAsStream("/icons/termora_32x32.png"))
        val trayIcon = TrayIcon(image)
        val dialog = JDialog()
        val trayPopup = JPopupMenu()

        dialog.isUndecorated = true
        dialog.isModal = false
        dialog.size = Dimension(0, 0)

        trayIcon.isImageAutoSize = true
        trayIcon.toolTip = Application.getName()

        trayPopup.add(I18n.getString("termora.exit")).addActionListener { quitHandler() }
        trayPopup.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {

            }

            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {
                SwingUtilities.invokeLater {
                    if (dialog.isVisible) {
                        dialog.isVisible = false
                    }
                }
            }

            override fun popupMenuCanceled(e: PopupMenuEvent?) {
                popupMenuWillBecomeInvisible(e)
            }

        })

        trayIcon.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                maybeShowPopup(e)
            }

            override fun mousePressed(e: MouseEvent) {
                maybeShowPopup(e)
            }

            private fun maybeShowPopup(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    val mouseLocation = MouseInfo.getPointerInfo().location
                    trayPopup.setLocation(mouseLocation.x, mouseLocation.y)
                    trayPopup.setInvoker(dialog)
                    dialog.isVisible = true
                    trayPopup.isVisible = true
                }
            }
        })

        dialog.addWindowFocusListener(object : WindowAdapter() {
            override fun windowLostFocus(e: WindowEvent) {
                dialog.isVisible = false
            }
        })

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

        // init native icon
        NativeIcons.folderIcon

        themeManager.change(theme, true)

        if (Application.isBetaVersion()) {
            FlatInspector.install("ctrl shift X")
        }

        UIManager.put(FlatClientProperties.FULL_WINDOW_CONTENT, true)
        UIManager.put(FlatClientProperties.USE_WINDOW_DECORATIONS, false)
        UIManager.put(FlatClientProperties.POPUP_FORCE_HEAVY_WEIGHT, true)

        UIManager.put("Component.arc", 5)
        UIManager.put("TextComponent.arc", UIManager.getInt("Component.arc"))
        UIManager.put("Component.hideMnemonics", true)

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

        if (SystemInfo.isLinux || SystemInfo.isWindows) {
            UIManager.put("TitlePane.centerTitle", true)
            UIManager.put("TitlePane.showIcon", false)
            UIManager.put("TitlePane.showIconInDialogs", false)
        }

        UIManager.put("Table.rowHeight", 24)
        UIManager.put("Table.focusCellHighlightBorder", FlatTableCellBorder.Default())
        UIManager.put("Table.focusSelectedCellHighlightBorder", FlatTableCellBorder.Default())

        UIManager.put("Tree.rowHeight", 24)
        UIManager.put("Tree.background", DynamicColor("window"))
        UIManager.put("Tree.showCellFocusIndicator", false)
        UIManager.put("Tree.repaintWholeRow", true)

        // Linux 更多的是尖锐风格
        if (SystemInfo.isMacOS || SystemInfo.isWindows) {
            val selectionInsets = Insets(0, 2, 0, 2)
            UIManager.put("Tree.selectionArc", UIManager.getInt("Component.arc"))
            UIManager.put("Tree.selectionInsets", selectionInsets)

            UIManager.put("List.selectionArc", UIManager.getInt("Component.arc"))
            UIManager.put("List.selectionInsets", selectionInsets)

            UIManager.put("ComboBox.selectionArc", UIManager.getInt("Component.arc"))
            UIManager.put("ComboBox.selectionInsets", selectionInsets)

            UIManager.put("Table.selectionArc", UIManager.getInt("Component.arc"))
            UIManager.put("Table.selectionInsets", selectionInsets)

            UIManager.put("MenuBar.selectionArc", UIManager.getInt("Component.arc"))
            UIManager.put("MenuBar.selectionInsets", selectionInsets)

            UIManager.put("MenuItem.selectionArc", UIManager.getInt("Component.arc"))
            UIManager.put("MenuItem.selectionInsets", selectionInsets)
        }

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
                val endpoints = listOf(
                    "https://api-eu.mixpanel.com",
                    "https://api-in.mixpanel.com",
                    "https://api.mixpanel.com",
                    "http://api.mixpanel.com",
                )
                for (endpoint in endpoints) {
                    try {
                        MixpanelAPI(
                            "$endpoint/track",
                            "$endpoint/engage",
                            "$endpoint/groups"
                        ).deliver(delivery, true)
                        break
                    } catch (e: Exception) {
                        if (log.isErrorEnabled) {
                            log.error(e.message, e)
                        }
                        continue
                    }
                }
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