package app.termora


import app.termora.actions.DataProvider
import app.termora.actions.DataProviderSupport
import app.termora.actions.DataProviders
import app.termora.actions.OpenHostAction
import app.termora.findeverywhere.FindEverywhereProvider
import app.termora.findeverywhere.FindEverywhereProviderExtension
import app.termora.findeverywhere.FindEverywhereResult
import app.termora.plugin.ExtensionManager
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import app.termora.plugin.internal.ssh.SSHProtocolProvider
import app.termora.terminal.DataKey
import app.termora.tree.NewHostTreeModel
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.ui.FlatRootPaneUI
import com.formdev.flatlaf.ui.FlatTitlePane
import com.formdev.flatlaf.util.SystemInfo
import com.jetbrains.JBR
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.action.ActionManager
import java.awt.*
import java.awt.event.*
import java.util.*
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.SwingUtilities.isEventDispatchThread
import javax.swing.UIManager


fun assertEventDispatchThread() {
    if (!isEventDispatchThread()) throw WrongThreadException("AWT EventQueue")
}


class TermoraFrame : JFrame(), DataProvider {

    private val layout get() = TermoraLayout.Layout
    private val titleBarHeight = computedTitleBarHeight()
    private val id = UUID.randomUUID().toString()
    private val windowScope = ApplicationScope.forWindowScope(this)
    private val tabbedPane = MyTabbedPane().apply { tabHeight = titleBarHeight }
    private val toolbar = TermoraToolBar(windowScope, this)
    private val terminalTabbed = TerminalTabbed(windowScope, toolbar, tabbedPane, layout)
    private val dataProviderSupport = DataProviderSupport()
    private var notifyListeners = emptyArray<NotifyListener>()
    private val moveMouseAdapter = createMoveMouseAdaptor()


    init {
        initView()
        initEvents()
    }

    private fun initEvents() {
        if (SystemInfo.isLinux) {
            toolbar.getJToolBar().addMouseListener(moveMouseAdapter)
            toolbar.getJToolBar().addMouseMotionListener(moveMouseAdapter)
        } else if (SystemInfo.isMacOS) {
            terminalTabbed.addMouseListener(moveMouseAdapter)
            terminalTabbed.addMouseMotionListener(moveMouseAdapter)

            tabbedPane.addMouseListener(moveMouseAdapter)
            tabbedPane.addMouseMotionListener(moveMouseAdapter)

            toolbar.getJToolBar().addMouseListener(moveMouseAdapter)
            toolbar.getJToolBar().addMouseMotionListener(moveMouseAdapter)
        }

        // FindEverywhere
        DynamicExtensionHandler.getInstance()
            .register(FindEverywhereProviderExtension::class.java, object : FindEverywhereProviderExtension {
                private val hostTreeModel get() = NewHostTreeModel.getInstance()

                private val provider = object : FindEverywhereProvider {
                    override fun find(pattern: String, scope: Scope): List<FindEverywhereResult> {
                        if (scope != windowScope) return emptyList()

                        var filter = hostTreeModel.root.getAllChildren()
                            .filter { it.isFolder.not() }
                            .map { it.host }

                        if (pattern.isNotBlank()) {
                            filter = filter.filter {
                                if (it.protocol == SSHProtocolProvider.PROTOCOL) {
                                    it.name.contains(pattern, true) || it.host.contains(pattern, true)
                                } else {
                                    it.name.contains(pattern, true)
                                }
                            }
                        }

                        return filter.map { HostFindEverywhereResult(it) }
                    }

                    override fun group(): String {
                        return I18n.getString("termora.find-everywhere.groups.open-new-hosts")
                    }

                    override fun order(): Int {
                        return Integer.MIN_VALUE + 2
                    }
                }

                override fun getFindEverywhereProvider(): FindEverywhereProvider {
                    return provider
                }

                private inner class HostFindEverywhereResult(val host: Host) : FindEverywhereResult {
                    private val showMoreInfo get() = EnableManager.getInstance().isShowMoreInfo()

                    override fun actionPerformed(e: ActionEvent) {
                        ActionManager.getInstance()
                            .getAction(OpenHostAction.OPEN_HOST)
                            ?.actionPerformed(OpenHostActionEvent(e.source, host, e))
                    }

                    override fun getIcon(isSelected: Boolean): Icon {
                        if (isSelected) {
                            if (!FlatLaf.isLafDark()) {
                                return Icons.terminal.dark
                            }
                        }
                        return Icons.terminal
                    }

                    override fun getText(isSelected: Boolean): String {
                        if (showMoreInfo) {
                            val color = UIManager.getColor(if (isSelected) "textHighlightText" else "textInactiveText")
                            val moreInfo = when (host.protocol) {
                                SSHProtocolProvider.PROTOCOL -> "${host.username}@${host.host}"
                                "Serial" -> host.options.serialComm.port
                                else -> StringUtils.EMPTY
                            }
                            if (moreInfo.isNotBlank()) {
                                return "<html>${host.name}&nbsp;&nbsp;&nbsp;&nbsp;<font color=rgb(${color.red},${color.green},${color.blue})>${moreInfo}</font></html>"
                            }
                        }
                        return host.name
                    }
                }

            }).let { Disposer.register(windowScope, it) }

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
            val sizes = listOf(16, 20, 24, 28, 32, 48, 64, 128)
            val loader = TermoraFrame::class.java.classLoader
            val images = sizes.mapNotNull { e ->
                loader.getResourceAsStream("icons/termora_${e}x${e}.png")?.use { ImageIO.read(it) }
            }
            iconImages = images
        }

        minimumSize = Dimension(640, 400)

        val glassPane = GlassPane()
        rootPane.glassPane = glassPane
        glassPane.isOpaque = false
        glassPane.isVisible = true

        for (extension in ExtensionManager.getInstance().getExtensions(GlassPaneAwareExtension::class.java)) {
            extension.setGlassPane(this, glassPane)
        }

        if (layout == TermoraLayout.Fence) {
            val fencePanel = TermoraFencePanel(terminalTabbed, tabbedPane, moveMouseAdapter)
            add(fencePanel, BorderLayout.CENTER)
            dataProviderSupport.addData(DataProviders.Welcome.HostTree, fencePanel.getHostTree())
            Disposer.register(windowScope, fencePanel)
        } else {
            val screenPanel = TermoraScreenPanel(windowScope, terminalTabbed)
            add(screenPanel, BorderLayout.CENTER)
            dataProviderSupport.addData(DataProviders.Welcome.HostTree, screenPanel.getHostTree())
        }

        Disposer.register(windowScope, terminalTabbed)

        dataProviderSupport.addData(DataProviders.TabbedPane, tabbedPane)
        dataProviderSupport.addData(DataProviders.TermoraFrame, this)
        dataProviderSupport.addData(DataProviders.WindowScope, windowScope)
    }

    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        return dataProviderSupport.getData(dataKey) ?: terminalTabbed.getData(dataKey)
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

    private fun computedTitleBarHeight(): Int {
        val tabHeight = UIManager.getInt("TabbedPane.tabHeight")
        if (SystemInfo.isWindows) {
            // Windows 10 会有1像素误差
            return tabHeight + if (SystemInfo.isWindows_11_orLater) 1 else 2
        } else if (SystemInfo.isLinux) {
            return tabHeight + 1
        }
        return tabHeight
    }

    private fun createMoveMouseAdaptor(): MouseAdapter {
        if (SystemInfo.isLinux) {
            return object : MouseAdapter() {
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

                JBR.getWindowDecorations().setCustomTitleBar(this, customTitleBar)

                return mouseAdapter
            }
        }

        return object : MouseAdapter() {}
    }


    private inner class GlassPane : JComponent() {

        init {
            isFocusable = false
        }

        override fun paintComponent(g2d: Graphics) {
            if (g2d !is Graphics2D) return
            val extensions = ExtensionManager.getInstance()
                .getExtensions(GlassPaneExtension::class.java)
            if (extensions.isNotEmpty()) {
                for (extension in extensions) {
                    g2d.save()
                    extension.paint(windowScope, this, g2d)
                    g2d.restore()
                }
            }
        }

        override fun contains(x: Int, y: Int): Boolean {
            return false
        }

    }
}