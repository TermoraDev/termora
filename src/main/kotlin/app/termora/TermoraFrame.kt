package app.termora


import app.termora.actions.DataProvider
import app.termora.actions.DataProviderSupport
import app.termora.actions.DataProviders
import app.termora.database.DatabaseManager
import app.termora.plugin.ExtensionManager
import app.termora.sftp.SFTPTab
import app.termora.terminal.DataKey
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.ui.FlatRootPaneUI
import com.formdev.flatlaf.ui.FlatTitlePane
import com.formdev.flatlaf.util.SystemInfo
import com.jetbrains.JBR
import org.apache.commons.lang3.ArrayUtils
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.util.*
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.SwingUtilities.isEventDispatchThread
import javax.swing.UIManager


fun assertEventDispatchThread() {
    if (!isEventDispatchThread()) throw WrongThreadException("AWT EventQueue")
}


class TermoraFrame : JFrame(), DataProvider {


    private val id = UUID.randomUUID().toString()
    private val windowScope = ApplicationScope.forWindowScope(this)
    private val tabbedPane = MyTabbedPane()
    private val toolbar = TermoraToolBar(windowScope, this)
    private val terminalTabbed = TerminalTabbed(windowScope, toolbar, tabbedPane)
    private val dataProviderSupport = DataProviderSupport()
    private val welcomePanel = WelcomePanel(windowScope)
    private val sftp get() = DatabaseManager.getInstance().sftp
    private var notifyListeners = emptyArray<NotifyListener>()


    init {
        initView()
        initEvents()
    }

    private fun initEvents() {
        if (SystemInfo.isLinux) {
            val mouseAdapter = object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    getMouseHandler()?.mouseClicked(e)
                }

                override fun mousePressed(e: MouseEvent) {
                    getMouseHandler()?.mousePressed(e)
                }

                override fun mouseDragged(e: MouseEvent) {
                    getMouseMotionListener()?.mouseDragged(
                        MouseEvent(
                            e.component,
                            e.id,
                            e.`when`,
                            e.modifiersEx,
                            e.x,
                            e.y,
                            e.clickCount,
                            e.isPopupTrigger,
                            e.button
                        )
                    )
                }

                private fun getMouseHandler(): MouseListener? {
                    return getHandler() as? MouseListener
                }

                private fun getMouseMotionListener(): MouseMotionListener? {
                    return getHandler() as? MouseMotionListener
                }

                private fun getHandler(): Any? {
                    val titlePane = getTitlePane() ?: return null
                    val handlerField = titlePane.javaClass.getDeclaredField("handler") ?: return null
                    handlerField.isAccessible = true
                    return handlerField.get(titlePane)
                }

                private fun getTitlePane(): FlatTitlePane? {
                    val ui = rootPane.ui as? FlatRootPaneUI ?: return null
                    val titlePaneField = ui.javaClass.getDeclaredField("titlePane")
                    titlePaneField.isAccessible = true
                    return titlePaneField.get(ui) as? FlatTitlePane
                }
            }
            toolbar.getJToolBar().addMouseListener(mouseAdapter)
            toolbar.getJToolBar().addMouseMotionListener(mouseAdapter)
        }

        /// force hit
        if (SystemInfo.isMacOS) {
            if (JBR.isWindowDecorationsSupported()) {
                val height = UIManager.getInt("TabbedPane.tabHeight") + tabbedPane.tabAreaInsets.top
                val customTitleBar = JBR.getWindowDecorations().createCustomTitleBar()
                customTitleBar.height = height.toFloat()

                val mouseAdapter = object : MouseAdapter() {

                    private fun hit(e: MouseEvent) {
                        if (e.source == tabbedPane) {
                            val index = tabbedPane.indexAtLocation(e.x, e.y)
                            if (index >= 0) {
                                return
                            }
                        }
                        customTitleBar.forceHitTest(false)
                    }

                    override fun mouseClicked(e: MouseEvent) {
                        hit(e)
                    }

                    override fun mousePressed(e: MouseEvent) {
                        hit(e)
                    }

                    override fun mouseReleased(e: MouseEvent) {
                        hit(e)
                    }

                    override fun mouseEntered(e: MouseEvent) {
                        hit(e)
                    }

                    override fun mouseDragged(e: MouseEvent) {
                        hit(e)
                    }

                    override fun mouseMoved(e: MouseEvent) {
                        hit(e)
                    }
                }

                terminalTabbed.addMouseListener(mouseAdapter)
                terminalTabbed.addMouseMotionListener(mouseAdapter)

                tabbedPane.addMouseListener(mouseAdapter)
                tabbedPane.addMouseMotionListener(mouseAdapter)

                toolbar.getJToolBar().addMouseListener(mouseAdapter)
                toolbar.getJToolBar().addMouseMotionListener(mouseAdapter)

                JBR.getWindowDecorations().setCustomTitleBar(this, customTitleBar)
            }
        }
    }


    private fun initView() {

        // macOS 要避开左边的控制栏
        if (SystemInfo.isMacOS) {
            tabbedPane.tabAreaInsets = Insets(0, 76, 0, 0)
        } else if (SystemInfo.isWindows) {
            // Windows 10 会有1像素误差
            tabbedPane.tabAreaInsets = Insets(if (SystemInfo.isWindows_11_orLater) 1 else 2, 2, 0, 0)
        } else if (SystemInfo.isLinux) {
            tabbedPane.tabAreaInsets = Insets(1, 2, 0, 0)
        }

        val height = UIManager.getInt("TabbedPane.tabHeight") + tabbedPane.tabAreaInsets.top

        if (SystemInfo.isWindows || SystemInfo.isLinux) {
            rootPane.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, true)
            rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICON, false)
            rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE, false)
            rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_HEIGHT, height)
        } else if (SystemInfo.isMacOS) {
            rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            rootPane.putClientProperty(
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_MEDIUM
            )
        }

        if (SystemInfo.isWindows || SystemInfo.isLinux) {
            val sizes = listOf(16, 20, 24, 28, 32, 48, 64)
            val loader = TermoraFrame::class.java.classLoader
            val images = sizes.mapNotNull { e ->
                loader.getResourceAsStream("icons/termora_${e}x${e}.png")?.use { ImageIO.read(it) }
            }
            iconImages = images
        }

        minimumSize = Dimension(640, 400)
        terminalTabbed.addTerminalTab(welcomePanel)

        // 下一次事件循环检测是否固定 SFTP
        if (sftp.pinTab) {
            SwingUtilities.invokeLater {
                terminalTabbed.addTerminalTab(SFTPTab(), false)
            }
        }

        val glassPane = GlassPane()
        rootPane.glassPane = glassPane
        glassPane.isOpaque = false
        glassPane.isVisible = true

        for (extension in ExtensionManager.getInstance().getExtensions(GlassPaneAwareExtension::class.java)) {
            extension.setGlassPane(this, glassPane)
        }


        Disposer.register(windowScope, terminalTabbed)
        add(terminalTabbed, BorderLayout.CENTER)

        dataProviderSupport.addData(DataProviders.TabbedPane, tabbedPane)
        dataProviderSupport.addData(DataProviders.TermoraFrame, this)
        dataProviderSupport.addData(DataProviders.WindowScope, windowScope)
    }

    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        return dataProviderSupport.getData(dataKey)
            ?: terminalTabbed.getData(dataKey)
            ?: welcomePanel.getData(dataKey)
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TermoraFrame

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun addNotifyListener(listener: NotifyListener) {
        notifyListeners += listener
    }

    fun removeNotifyListener(listener: NotifyListener) {
        notifyListeners = ArrayUtils.removeElements(notifyListeners, listener)
    }

    override fun addNotify() {
        super.addNotify()
        notifyListeners.forEach { it.addNotify() }
    }


    private class GlassPane : JComponent() {

        init {
            isFocusable = false
        }

        override fun paintComponent(g2d: Graphics) {
            if (g2d !is Graphics2D) return
            val extensions = ExtensionManager.getInstance()
                .getExtensions(GlassPaneExtension::class.java)
            if (extensions.isNotEmpty()) {
                for (extension in extensions) {
                    if (extension.paint(this, g2d)) {
                        return
                    }
                }
            }
        }

        override fun contains(x: Int, y: Int): Boolean {
            return false
        }

    }
}