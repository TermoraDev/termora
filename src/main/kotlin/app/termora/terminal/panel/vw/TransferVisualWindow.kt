package app.termora.terminal.panel.vw

import app.termora.*
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import app.termora.plugin.internal.badge.Badge
import app.termora.plugin.internal.ssh.SSHTerminalTab
import app.termora.plugin.internal.ssh.SSHTerminalTab.Companion.SSHSession
import app.termora.terminal.DataKey
import app.termora.terminal.DataListener
import app.termora.transfer.*
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.icons.FlatOptionPaneErrorIcon
import com.formdev.flatlaf.util.SystemInfo
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.sftp.client.SftpClientFactory
import org.jdesktop.swingx.JXBusyLabel
import org.jdesktop.swingx.JXHyperlink
import org.jdesktop.swingx.JXTree
import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.event.*
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import javax.swing.*
import kotlin.io.path.absolutePathString
import kotlin.math.max
import kotlin.reflect.cast
import kotlin.time.Duration.Companion.milliseconds


internal class TransferVisualWindow(tab: SSHTerminalTab, visualWindowManager: VisualWindowManager) :
    SSHVisualWindow(tab, "Transfer", visualWindowManager) {

    companion object {
        private val log = LoggerFactory.getLogger(TransferVisualWindow::class.java)
    }

    private enum class State {
        Connecting,
        Transfer,
        Failed,
    }

    private val executorService = Executors.newVirtualThreadPerTaskExecutor()
    private val coroutineDispatcher = executorService.asCoroutineDispatcher()
    private val coroutineScope = CoroutineScope(coroutineDispatcher)
    private val cardLayout = CardLayout()
    private val panel = JPanel(cardLayout)
    private val connectingPanel = ConnectingPanel()
    private val connectFailedPanel = ConnectFailedPanel()
    private val transferManager = TransferTableModel(coroutineScope)
    private val disposable = Disposer.newDisposable()
    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val questionBtn = JButton(Icons.questionMark)
    private val downloadBtn = JButton(Icons.download)
    private val badgePresentation = Badge.getInstance(tab.windowScope)
        .addBadge(downloadBtn).apply { visible = false }


    init {
        initViews()
        initEvents()
        initVisualWindowPanel()
    }


    private fun initViews() {
        title = "SFTP"

        panel.add(connectingPanel, State.Connecting.name)
        panel.add(connectFailedPanel, State.Failed.name)


        add(panel, BorderLayout.CENTER)
    }

    private fun initEvents() {
        Disposer.register(tab, this)
        Disposer.register(this, disposable)
        Disposer.register(disposable, transferManager)
        Disposer.register(disposable, badgePresentation)

        connectingPanel.busyLabel.isBusy = true

        val terminal = tab.getData(DataProviders.TerminalPanel)?.getData(DataProviders.Terminal)
        terminal?.getTerminalModel()?.addDataListener(object : DataListener {
            override fun onChanged(key: DataKey<*>, data: Any) {
                // https://github.com/TermoraDev/termora/pull/244
                if (key == DataKey.CurrentDir) {
                    val dir = DataKey.CurrentDir.clazz.cast(data)
                    val navigator = getTransportNavigator() ?: return
                    val loader = navigator.loader
                    if (loader.isOpened().not()) return
                    val fileSystem = loader.getSyncTransportSupport().getFileSystem()
                    val path = fileSystem.getPath(dir)
                    navigator.navigateTo(path.absolutePathString())
                }
            }
        })

        downloadBtn.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val dialog = DownloadDialog()
                dialog.iconImages = owner.iconImages
                dialog.setLocationRelativeTo(downloadBtn)
                dialog.setLocation(dialog.x, downloadBtn.locationOnScreen.y + downloadBtn.height + 2)
                dialog.isVisible = true
            }
        })

        transferManager.addTransferListener(object : TransferListener {
            override fun onTransferCountChanged() {
                val oldVisible = badgePresentation.visible
                val newVisible = transferManager.getTransferCount() > 0
                if (oldVisible != newVisible) {
                    badgePresentation.visible = newVisible
                    downloadBtn.repaint()
                }
            }
        })

        questionBtn.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                Application.browse(URI.create("https://github.com/TermoraDev/termora/pull/690"))
            }
        })

        // 立即连接
        connect()
    }

    private fun connect() {
        connectingPanel.busyLabel.isBusy = true
        cardLayout.show(panel, State.Connecting.name)

        coroutineScope.launch {

            try {
                val session = getSession()
                val fileSystem = SftpClientFactory.instance().createSftpFileSystem(session)
                val support = DefaultTransportSupport(fileSystem, fileSystem.defaultDir)
                withContext(Dispatchers.Swing) {
                    val internalTransferManager = MyInternalTransferManager()
                    val transportPanel = TransportPanel(
                        internalTransferManager, tab.host,
                        object : TransportSupportLoader {
                            override suspend fun getTransportSupport(): TransportSupport {
                                return support
                            }

                            override fun getSyncTransportSupport(): TransportSupport {
                                return support
                            }

                            override fun isLoaded(): Boolean {
                                return true
                            }
                        })
                    internalTransferManager.setTransferPanel(transportPanel)
                    Disposer.register(transportPanel, object : Disposable {
                        override fun dispose() {
                            panel.remove(transportPanel)
                            IOUtils.closeQuietly(fileSystem)
                            swingCoroutineScope.launch {
                                connectFailedPanel.errorLabel.text = I18n.getString("termora.transport.sftp.closed")
                                cardLayout.show(panel, State.Failed.name)
                            }
                        }
                    })
                    Disposer.register(disposable, transportPanel)

                    // 如果 session 关闭，立即销毁 Transfer
                    session.addCloseFutureListener { Disposer.dispose(transportPanel) }
                    panel.add(transportPanel, State.Transfer.name)
                    cardLayout.show(panel, State.Transfer.name)
                }
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
                withContext(Dispatchers.Swing) {
                    connectFailedPanel.errorLabel.text = ExceptionUtils.getRootCauseMessage(e)
                    cardLayout.show(panel, State.Failed.name)
                }
            } finally {
                swingCoroutineScope.launch { connectingPanel.busyLabel.isBusy = false }
            }
        }
    }

    private suspend fun getSession(): ClientSession {
        while (coroutineScope.isActive) {
            val session = tab.getData(SSHSession)
            if (session == null) {
                delay(250.milliseconds)
                continue
            }
            return session
        }
        throw IllegalStateException("Session is null")
    }

    private fun getTransportNavigator(): TransportPanel? {
        for (i in 0 until panel.componentCount) {
            val c = panel.getComponent(i)
            if (c is TransportPanel) {
                return c
            }
        }
        return null
    }

    override fun beforeClose(): Boolean {
        if (transferManager.getTransferCount() > 0) {
            return OptionPane.showConfirmDialog(
                owner,
                I18n.getString("termora.transport.sftp.close-tab"),
                messageType = JOptionPane.QUESTION_MESSAGE,
                optionType = JOptionPane.OK_CANCEL_OPTION
            ) == JOptionPane.OK_OPTION
        }
        return super.beforeClose()
    }

    override fun dispose() {
        coroutineScope.cancel()
        coroutineDispatcher.close()
        executorService.shutdownNow()
        connectingPanel.busyLabel.isBusy = false
        super.dispose()
    }

    override fun toolbarButtons(): List<Pair<JButton, Position>> {
        return listOf(downloadBtn to Position.Left, questionBtn to Position.Right)
    }

    private inner class DownloadDialog() : JDialog() {

        init {
            size = getMySize()
            isModal = false
            title = I18n.getString("termora.transport.sftp")
            layout = BorderLayout()

            if (SystemInfo.isMacOS) {
                rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
                rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            }

            add(createCenterPanel(), BorderLayout.CENTER)
            val window = this

            val inputMap = rootPane.getInputMap(WHEN_IN_FOCUSED_WINDOW)
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close")
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, toolkit.menuShortcutKeyMaskEx), "close")
            rootPane.actionMap.put("close", object : AnAction() {
                override fun actionPerformed(evt: AnActionEvent) {
                    if (hasPopupMenus().not()) {
                        window.dispose()
                    }
                }
            })


            // 判断失去焦点
            val awtEventListener = object : AWTEventListener {
                override fun eventDispatched(event: AWTEvent) {
                    if (event !is MouseEvent) return
                    if (event.id != MouseEvent.MOUSE_PRESSED) return
                    val ancestor = SwingUtilities.getWindowAncestor(event.component)
                    if (ancestor == window) return
                    if (ancestor is Window && getOwners(ancestor).contains(window)) return
                    // JTreeTable 比较特殊，要特别判断
                    if (isFocused && event.component is JXTree) return
                    window.dispose()
                }

                private fun getOwners(window: Window): List<Window> {
                    val owners = mutableListOf<Window>()
                    var owner: Window? = window.owner
                    while (owner != null) {
                        owners.add(owner)
                        owner = owner.owner
                    }
                    return owners
                }
            }

            // 监听全局事件
            toolkit.addAWTEventListener(
                awtEventListener,
                MouseEvent.MOUSE_EVENT_MASK
            )

            addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent) {
                    removeWindowListener(this)
                    toolkit.removeAWTEventListener(awtEventListener)
                    properties.putString("VisualWindow.DownloadDialog.location.width", width.toString())
                    properties.putString("VisualWindow.DownloadDialog.location.height", height.toString())
                }
            })

        }

        private fun getMySize(): Dimension {
            val size = Dimension(UIManager.getInt("Dialog.width") - 150, UIManager.getInt("Dialog.height") - 100)
            val width = properties.getString(
                "VisualWindow.DownloadDialog.location.width",
                size.width.toString()
            ).toIntOrNull() ?: size.width
            val height = properties.getString(
                "VisualWindow.DownloadDialog.location.height",
                size.height.toString()
            ).toIntOrNull() ?: size.height
            return Dimension(max(width, 250), max(height, 150))
        }

        private fun hasPopupMenus(): Boolean {
            val c = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            if (c != null) {
                val popups: List<JPopupMenu> = SwingUtils.getDescendantsOfType(
                    JPopupMenu::class.java,
                    c as Container, true
                )

                var openPopup = false
                for (p in popups) {
                    p.isVisible = false
                    openPopup = true
                }

                val window = c as? Window ?: SwingUtilities.windowForComponent(c)
                if (window != null) {
                    val windows = window.ownedWindows
                    for (w in windows) {
                        if (w.isVisible && w.javaClass.getName().endsWith("HeavyWeightWindow")) {
                            openPopup = true
                            w.dispose()
                        }
                    }
                }

                if (openPopup) {
                    return true
                }
            }
            return false
        }

        private fun createCenterPanel(): JComponent {
            val table = TransferTable(coroutineScope, transferManager)
            val scrollPane = JScrollPane(table)
            scrollPane.border = BorderFactory.createEmptyBorder()
            addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent) {
                    removeWindowListener(this)
                    Disposer.dispose(table)
                }
            })
            return scrollPane
        }

        override fun addNotify() {
            super.addNotify()

            if (SystemInfo.isMacOS) {
                NativeMacLibrary.setControlsVisible(this, false)
            } else if (SystemInfo.isWindows || SystemInfo.isLinux) {
                rootPane.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, true)
                rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICONIFFY, false)
                rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICON, false)
                rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_MAXIMIZE, false)
                rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE, false)
                rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_CLOSE, false)
            }
        }

    }

    private inner class MyInternalTransferManager() : InternalTransferManager {
        private lateinit var internalTransferManager: InternalTransferManager

        fun setTransferPanel(transportPanel: TransportPanel) {
            internalTransferManager = createInternalTransferManager(transportPanel)
        }

        override fun canTransfer(paths: List<Path>): Boolean {
            return paths.isNotEmpty()
        }

        override fun addTransfer(
            paths: List<Pair<Path, TransportTableModel.Attributes>>,
            mode: InternalTransferManager.TransferMode
        ): CompletableFuture<Unit> {

            if (mode == InternalTransferManager.TransferMode.Transfer) {
                val future = CompletableFuture<Unit>()
                val chooser = FileChooser()
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                chooser.allowsMultiSelection = false
                chooser.showOpenDialog(owner).whenComplete { files, e ->
                    if (e != null) {
                        future.completeExceptionally(e)
                    } else if (files.isEmpty()) {
                        future.complete(Unit)
                    } else {
                        coroutineScope.launch(Dispatchers.Swing) {
                            try {
                                addTransfer(paths, files.first().toPath(), mode)
                                    .thenApply { future.complete(it) }
                                    .exceptionally { future.completeExceptionally(it) }
                            } catch (e: Exception) {
                                future.completeExceptionally(e)
                            }
                        }
                    }
                }
                return future
            }

            return internalTransferManager.addTransfer(paths, mode)
        }

        override fun addTransfer(
            paths: List<Pair<Path, TransportTableModel.Attributes>>,
            targetWorkdir: Path,
            mode: InternalTransferManager.TransferMode
        ): CompletableFuture<Unit> {
            return internalTransferManager.addTransfer(paths, targetWorkdir, mode)
        }

        override fun addHighTransfer(source: Path, target: Path): String {
            return internalTransferManager.addHighTransfer(source, target)
        }

        override fun addTransferListener(listener: TransferListener): Disposable {
            return transferManager.addTransferListener(listener)
        }


        private fun createWorkdirProvider(transportPanel: TransportPanel): DefaultInternalTransferManager.WorkdirProvider {
            return object : DefaultInternalTransferManager.WorkdirProvider {
                override fun getWorkdir(): Path? {
                    return transportPanel.workdir
                }
            }
        }

        private fun createInternalTransferManager(transportPanel: TransportPanel): InternalTransferManager {
            return DefaultInternalTransferManager(
                { owner },
                coroutineScope,
                transferManager,
                object : DefaultInternalTransferManager.WorkdirProvider {
                    override fun getWorkdir() = null
                },
                createWorkdirProvider(transportPanel)
            )
        }

    }


    private class ConnectingPanel : JPanel(BorderLayout()) {
        val busyLabel = JXBusyLabel()

        init {
            initView()
        }

        private fun initView() {
            val formMargin = "7dlu"
            val layout = FormLayout(
                "default:grow, pref, default:grow",
                "40dlu, pref, $formMargin, pref"
            )

            val label = JLabel(I18n.getString("termora.transport.sftp.connecting"))
            label.horizontalAlignment = SwingConstants.CENTER

            busyLabel.horizontalAlignment = SwingConstants.CENTER
            busyLabel.verticalAlignment = SwingConstants.CENTER

            val builder = FormBuilder.create().layout(layout).debug(false)
            builder.add(busyLabel).xy(2, 2, "fill, center")
            builder.add(label).xy(2, 4)
            add(builder.build(), BorderLayout.CENTER)
        }

    }

    private inner class ConnectFailedPanel : JPanel(BorderLayout()) {
        val errorLabel = JLabel()

        init {
            initView()
        }

        private fun initView() {
            val formMargin = "4dlu"
            val layout = FormLayout(
                "default:grow, pref, default:grow",
                "40dlu, pref, $formMargin, pref, $formMargin, pref"
            )

            errorLabel.horizontalAlignment = SwingConstants.CENTER

            val builder = FormBuilder.create().layout(layout).debug(false)
            builder.add(FlatOptionPaneErrorIcon()).xy(2, 2)
            builder.add(errorLabel).xyw(1, 4, 3, "fill, center")
            builder.add(JXHyperlink(object : AbstractAction(I18n.getString("termora.transport.sftp.retry")) {
                override fun actionPerformed(e: ActionEvent) {
                    connect()
                }
            }).apply {
                horizontalAlignment = SwingConstants.CENTER
                verticalAlignment = SwingConstants.CENTER
                isFocusable = false
            }).xy(2, 6)
            add(builder.build(), BorderLayout.CENTER)
        }
    }
}