package app.termora

import app.termora.findeverywhere.FindEverywhere
import app.termora.highlight.KeywordHighlightDialog
import app.termora.keymgr.KeyManagerDialog
import app.termora.macro.MacroAction
import app.termora.tlog.TerminalLoggerAction
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.FlatDesktop
import com.formdev.flatlaf.util.SystemInfo
import com.jetbrains.JBR
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.JXEditorPane
import org.jdesktop.swingx.action.ActionManager
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.awt.Insets
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.*
import java.net.URI
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.SwingUtilities.isEventDispatchThread
import javax.swing.event.HyperlinkEvent
import kotlin.concurrent.fixedRateTimer
import kotlin.math.max
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

fun assertEventDispatchThread() {
    if (!isEventDispatchThread()) throw WrongThreadException("AWT EventQueue")
}


class TermoraFrame : JFrame() {

    companion object {
        private val log = LoggerFactory.getLogger(TermoraFrame::class.java)
    }

    private val toolbar = JToolBar()
    private val tabbedPane = MyTabbedPane()
    private lateinit var terminalTabbed: TerminalTabbed
    private val disposable = Disposer.newDisposable()
    private val isWindowDecorationsSupported by lazy { JBR.isWindowDecorationsSupported() }
    private val titleBar = LogicCustomTitleBar.createCustomTitleBar(this)
    private val updaterManager get() = UpdaterManager.instance

    private val preferencesHandler = object : Runnable {
        override fun run() {
            val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow ?: this@TermoraFrame
            if (owner != this@TermoraFrame) {
                return
            }

            val that = this
            FlatDesktop.setPreferencesHandler {}
            val dialog = SettingsDialog(owner)
            dialog.addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent) {
                    FlatDesktop.setPreferencesHandler(that)
                }
            })
            dialog.isVisible = true
        }
    }

    init {
        initActions()
        initView()
        initEvents()
        initDesktopHandler()
        scheduleUpdate()
    }

    private fun initEvents() {

        // 监听窗口大小变动，然后修改边距避开控制按钮
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                if (SystemInfo.isMacOS) {
                    val left = titleBar.leftInset.toInt()
                    if (tabbedPane.tabAreaInsets.left != left) {
                        tabbedPane.tabAreaInsets = Insets(0, left, 0, 0)
                    }
                } else if (SystemInfo.isWindows || SystemInfo.isLinux) {

                    val right = titleBar.rightInset.toInt()

                    for (i in 0 until toolbar.componentCount) {
                        val c = toolbar.getComponent(i)
                        if (c.name == "spacing") {
                            if (c.width == right) {
                                return
                            }
                            toolbar.remove(i)
                            break
                        }
                    }

                    if (right > 0) {
                        val spacing = Box.createHorizontalStrut(right)
                        spacing.name = "spacing"
                        toolbar.add(spacing)
                    }
                }
            }
        })

        forceHitTest()

        // macos 需要判断是否全部删除
        // 当 Tab 为 0 的时候，需要加一个边距，避开控制栏
        if (SystemInfo.isMacOS && isWindowDecorationsSupported) {
            tabbedPane.addChangeListener {
                tabbedPane.leadingComponent = if (tabbedPane.tabCount == 0) {
                    Box.createHorizontalStrut(titleBar.leftInset.toInt())
                } else {
                    null
                }
            }
        }

        // global shortcuts
        rootPane.actionMap.put(Actions.FIND_EVERYWHERE, ActionManager.getInstance().getAction(Actions.FIND_EVERYWHERE))
        rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_T, toolkit.menuShortcutKeyMaskEx), Actions.FIND_EVERYWHERE)

        // double shift
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(object : KeyEventDispatcher {
            private var lastTime = -1L

            override fun dispatchKeyEvent(e: KeyEvent): Boolean {
                if (e.keyCode == KeyEvent.VK_SHIFT && e.id == KeyEvent.KEY_PRESSED) {
                    val now = System.currentTimeMillis()
                    if (now - 250 < lastTime) {
                        ActionManager.getInstance().getAction(Actions.FIND_EVERYWHERE)
                            .actionPerformed(ActionEvent(rootPane, ActionEvent.ACTION_PERFORMED, StringUtils.EMPTY))
                    }
                    lastTime = now
                } else if (e.keyCode != KeyEvent.VK_SHIFT) { // 如果不是 Shift 键，那么就阻断了连续性，重置时间
                    lastTime = -1
                }
                return false
            }

        })

        // 监听主题变化 需要动态修改控制栏颜色
        if (SystemInfo.isWindows && isWindowDecorationsSupported) {
            ThemeManager.instance.addThemeChangeListener(object : ThemeChangeListener {
                override fun onChanged() {
                    titleBar.putProperty("controls.dark", FlatLaf.isLafDark())
                }
            })
        }


        // dispose
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {

                Disposer.dispose(disposable)
                Disposer.dispose(ApplicationDisposable.instance)

                try {
                    Disposer.getTree().assertIsEmpty(true)
                } catch (e: Exception) {
                    log.error(e.message)
                }
                exitProcess(0)
            }
        })


    }


    private fun initActions() {
        // SETTING
        ActionManager.getInstance().addAction(Actions.SETTING, object : AnAction(
            I18n.getString("termora.setting"),
            Icons.settings
        ) {
            override fun actionPerformed(e: ActionEvent) {
                preferencesHandler.run()
            }
        })


        // MULTIPLE
        ActionManager.getInstance().addAction(Actions.MULTIPLE, object : AnAction(
            I18n.getString("termora.tools.multiple"),
            Icons.vcs
        ) {
            init {
                setStateAction()
            }

            override fun actionPerformed(evt: ActionEvent) {
                TerminalPanelFactory.instance.repaintAll()
            }
        })


        // Keyword Highlight
        ActionManager.getInstance().addAction(Actions.KEYWORD_HIGHLIGHT_EVERYWHERE, object : AnAction(
            I18n.getString("termora.highlight"),
            Icons.edit
        ) {
            override fun actionPerformed(evt: ActionEvent) {
                KeywordHighlightDialog(this@TermoraFrame).isVisible = true
            }
        })

        // app update
        ActionManager.getInstance().addAction(Actions.APP_UPDATE, object :
            AnAction(
                StringUtils.EMPTY,
                Icons.ideUpdate
            ) {
            init {
                isEnabled = false
            }

            override fun actionPerformed(evt: ActionEvent) {
                showUpdateDialog()
            }
        })

        // 终端日志记录
        ActionManager.getInstance().addAction(Actions.TERMINAL_LOGGER, TerminalLoggerAction())

        // macro
        ActionManager.getInstance().addAction(Actions.MACRO, MacroAction())

        // FIND_EVERYWHERE
        ActionManager.getInstance().addAction(Actions.FIND_EVERYWHERE, object : AnAction(
            I18n.getString("termora.find-everywhere"),
            Icons.find
        ) {
            override fun actionPerformed(evt: ActionEvent) {
                if (this.isEnabled) {
                    val focusWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
                    val frame = this@TermoraFrame
                    if (focusWindow == frame) {
                        FindEverywhere(frame).isVisible = true
                    }
                }
            }
        })

        // Key manager
        ActionManager.getInstance().addAction(Actions.KEY_MANAGER, object : AnAction(
            I18n.getString("termora.keymgr.title"),
            Icons.greyKey
        ) {
            override fun actionPerformed(evt: ActionEvent) {
                if (this.isEnabled) {
                    KeyManagerDialog(this@TermoraFrame).isVisible = true
                }
            }
        })

    }

    private fun initView() {
        if (isWindowDecorationsSupported) {
            titleBar.height = UIManager.getInt("TabbedPane.tabHeight").toFloat()
            titleBar.putProperty("controls.dark", FlatLaf.isLafDark())
            JBR.getWindowDecorations().setCustomTitleBar(this, titleBar)
        }

        if (SystemInfo.isLinux) {
            rootPane.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, true)
            rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_HEIGHT, UIManager.getInt("TabbedPane.tabHeight"))
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
        terminalTabbed = TerminalTabbed(toolbar, tabbedPane).apply {
            Application.registerService(TerminalTabbedManager::class, this)
        }
        terminalTabbed.addTab(WelcomePanel())

        // macOS 要避开左边的控制栏
        if (SystemInfo.isMacOS) {
            val left = max(titleBar.leftInset.toInt(), 76)
            if (tabbedPane.tabCount == 0) {
                tabbedPane.leadingComponent = Box.createHorizontalStrut(left)
            } else {
                tabbedPane.tabAreaInsets = Insets(0, left, 0, 0)
            }
        }

        Disposer.register(disposable, terminalTabbed)
        add(terminalTabbed)

    }

    private fun showUpdateDialog() {
        val lastVersion = updaterManager.lastVersion
        val editorPane = JXEditorPane()
        editorPane.contentType = "text/html"
        editorPane.text = lastVersion.htmlBody
        editorPane.isEditable = false
        editorPane.addHyperlinkListener {
            if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                Application.browse(it.url.toURI())
            }
        }
        editorPane.background = DynamicColor("window")
        val scrollPane = JScrollPane(editorPane)
        scrollPane.border = BorderFactory.createEmptyBorder()
        scrollPane.preferredSize = Dimension(
            UIManager.getInt("Dialog.width") - 100,
            UIManager.getInt("Dialog.height") - 100
        )

        val option = OptionPane.showConfirmDialog(
            this,
            scrollPane,
            title = I18n.getString("termora.update.title"),
            messageType = JOptionPane.PLAIN_MESSAGE,
            optionType = JOptionPane.YES_NO_CANCEL_OPTION,
            options = arrayOf(
                I18n.getString("termora.update.update"),
                I18n.getString("termora.update.ignore"),
                I18n.getString("termora.cancel")
            ),
            initialValue = I18n.getString("termora.update.update")
        )
        if (option == JOptionPane.CANCEL_OPTION) {
            return
        } else if (option == JOptionPane.NO_OPTION) {
            ActionManager.getInstance().setEnabled(Actions.APP_UPDATE, false)
            updaterManager.ignore(updaterManager.lastVersion.version)
        } else if (option == JOptionPane.YES_OPTION) {
            ActionManager.getInstance()
                .setEnabled(Actions.APP_UPDATE, false)
            Application.browse(URI.create("https://github.com/TermoraDev/termora/releases/tag/${lastVersion.version}"))
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun scheduleUpdate() {
        fixedRateTimer(
            name = "check-update-timer",
            initialDelay = 3.minutes.inWholeMilliseconds,
            period = 5.hours.inWholeMilliseconds, daemon = true
        ) {
            GlobalScope.launch(Dispatchers.IO) { supervisorScope { launch { checkUpdate() } } }
        }
    }

    private suspend fun checkUpdate() {

        val latestVersion = updaterManager.fetchLatestVersion()
        if (latestVersion.isSelf) {
            return
        }

        val newVersion = Version(latestVersion.version)
        val version = Version(Application.getVersion())
        if (newVersion <= version) {
            return
        }

        if (updaterManager.isIgnored(latestVersion.version)) {
            return
        }

        withContext(Dispatchers.Swing) {
            ActionManager.getInstance()
                .setEnabled(Actions.APP_UPDATE, true)
        }

    }

    private fun forceHitTest() {
        val mouseAdapter = object : MouseAdapter() {

            private fun hit(e: MouseEvent) {
                if (e.source == tabbedPane) {
                    val index = tabbedPane.indexAtLocation(e.x, e.y)
                    if (index >= 0) {
                        return
                    }
                }
                titleBar.forceHitTest(false)
            }

            override fun mouseClicked(e: MouseEvent) {
                hit(e)
            }

            override fun mousePressed(e: MouseEvent) {
                if (e.source == toolbar) {
                    if (!isWindowDecorationsSupported && SwingUtilities.isLeftMouseButton(e)) {
                        if (JBR.isWindowMoveSupported()) {
                            JBR.getWindowMove().startMovingTogetherWithMouse(this@TermoraFrame, e.button)
                        }
                    }
                }
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

        toolbar.addMouseListener(mouseAdapter)
        toolbar.addMouseMotionListener(mouseAdapter)
    }

    private fun initDesktopHandler() {
        if (SystemInfo.isMacOS) {
            FlatDesktop.setPreferencesHandler {
                preferencesHandler.run()
            }
        }
    }
}