package app.termora.sftp

import app.termora.Disposable
import app.termora.Disposer
import app.termora.Host
import app.termora.I18n
import app.termora.actions.DataProvider
import app.termora.database.DatabaseManager
import app.termora.protocol.FileObjectHandler
import app.termora.protocol.FileObjectRequest
import app.termora.protocol.TransferProtocolProvider
import app.termora.terminal.DataKey
import app.termora.tree.NewHostTree
import app.termora.tree.TreeUtils
import com.formdev.flatlaf.icons.FlatOptionPaneErrorIcon
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.commons.vfs2.FileObject
import org.jdesktop.swingx.JXBusyLabel
import org.jdesktop.swingx.JXHyperlink
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener

class SFTPFileSystemViewPanel(
    var host: Host? = null,
    private val transportManager: TransportManager,
) : JPanel(BorderLayout()), Disposable, DataProvider {

    companion object {
        private val log = LoggerFactory.getLogger(SFTPFileSystemViewPanel::class.java)
    }

    enum class State {
        Initialized,
        Connecting,
        Connected,
        ConnectFailed,
    }

    @Volatile
    var state = State.Initialized
        private set
    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectingPanel = ConnectingPanel()
    private val selectHostPanel = SelectHostPanel()
    private val connectFailedPanel = ConnectFailedPanel()
    private val isDisposed = AtomicBoolean(false)
    private val that = this
    private val properties get() = DatabaseManager.getInstance().properties

    private var handler: FileObjectHandler? = null
    private var fileSystemPanel: FileSystemViewPanel? = null


    init {
        initView()
        initEvents()
    }

    private fun initView() {
        cardPanel.add(selectHostPanel, State.Initialized.name)
        cardPanel.add(connectingPanel, State.Connecting.name)
        cardPanel.add(connectFailedPanel, State.ConnectFailed.name)
        cardLayout.show(cardPanel, State.Initialized.name)
        add(cardPanel, BorderLayout.CENTER)
    }

    private fun initEvents() {
        Disposer.register(this, selectHostPanel)
    }

    fun connect() {
        coroutineScope.launch {
            if (state != State.Connecting) {
                state = State.Connecting

                withContext(Dispatchers.Swing) {
                    connectingPanel.start()
                    cardLayout.show(cardPanel, State.Connecting.name)
                }

                runCatching { doConnect() }.onFailure {
                    if (log.isErrorEnabled) {
                        log.error(it.message, it)
                    }
                    withContext(Dispatchers.Swing) {
                        state = State.ConnectFailed
                        connectFailedPanel.errorLabel.text = ExceptionUtils.getRootCauseMessage(it)
                        cardLayout.show(cardPanel, State.ConnectFailed.name)
                    }
                }

                withContext(Dispatchers.Swing) {
                    connectingPanel.stop()
                }
            }
        }
    }

    private suspend fun doConnect() {
        val thisHost = this.host ?: return

        closeIO()

        val file: FileObject
        val provider = TransferProtocolProvider.valueOf(thisHost.protocol)
            ?: throw IllegalStateException("Protocol ${thisHost.protocol} not supported")

        try {
            val owner = SwingUtilities.getWindowAncestor(that)
            val requester = FileObjectRequest(host = thisHost, owner = owner)
            provider.getRootFileObject(requester)
            val handler = provider.getRootFileObject(requester).apply { handler = this }
            file = handler.file
            Disposer.register(handler, object : Disposable {
                override fun dispose() {
                    onClose()
                }
            })
        } catch (e: Exception) {
            closeIO()
            throw e
        }

        if (isDisposed.get()) {
            throw IllegalStateException("Closed")
        }


        withContext(Dispatchers.Swing) {
            state = State.Connected
            val fileSystemPanel = FileSystemViewPanel(thisHost, file, transportManager, coroutineScope)
            cardPanel.add(fileSystemPanel, State.Connected.name)
            cardLayout.show(cardPanel, State.Connected.name)
            that.fileSystemPanel = fileSystemPanel
        }

    }

    private fun onClose() {
        if (isDisposed.get()) {
            return
        }

        SwingUtilities.invokeLater {
            closeIO()
            state = State.ConnectFailed
            connectFailedPanel.errorLabel.text = I18n.getString("termora.transport.sftp.closed")
            cardLayout.show(cardPanel, State.ConnectFailed.name)
        }
    }

    private fun closeIO() {
        val host = host

        fileSystemPanel?.let { Disposer.dispose(it) }
        fileSystemPanel = null

        handler?.let { Disposer.dispose(it) }
        handler = null

        if (host != null && log.isInfoEnabled) {
            log.info("Sftp ${host.name} is closed")
        }
    }

    override fun dispose() {
        if (isDisposed.compareAndSet(false, true)) {
            closeIO()
            coroutineScope.cancel()
        }
    }

    private class ConnectingPanel : JPanel(BorderLayout()) {
        private val busyLabel = JXBusyLabel()

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

        fun start() {
            busyLabel.isBusy = true
        }

        fun stop() {
            busyLabel.isBusy = false
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
                "40dlu, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
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
            builder.add(JXHyperlink(object :
                AbstractAction(I18n.getString("termora.transport.sftp.select-another-host")) {
                override fun actionPerformed(e: ActionEvent) {
                    state = State.Initialized
                    that.setTabTitle(I18n.getString("termora.transport.sftp.select-host"))
                    cardLayout.show(cardPanel, State.Initialized.name)
                }
            }).apply {
                horizontalAlignment = SwingConstants.CENTER
                verticalAlignment = SwingConstants.CENTER
                isFocusable = false
            }).xy(2, 8)
            add(builder.build(), BorderLayout.CENTER)
        }
    }

    private inner class SelectHostPanel : JPanel(BorderLayout()), Disposable {
        private val tree = NewHostTree()

        init {
            initView()
            initEvents()
        }

        private fun initView() {
            tree.contextmenu = false
            tree.dragEnabled = false
            tree.doubleClickConnection = false

            val scrollPane = JScrollPane(tree)
            scrollPane.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            add(scrollPane, BorderLayout.CENTER)

            TreeUtils.loadExpansionState(tree, properties.getString("SFTPTabbed.Tree.state", StringUtils.EMPTY))
        }

        private fun initEvents() {

            Disposer.register(this, tree)

            tree.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (SwingUtilities.isLeftMouseButton(e) && e.clickCount % 2 == 0) {
                        val node = tree.getLastSelectedPathNode() ?: return
                        if (node.isFolder) return
                        val host = node.data as Host
                        selectHost(host)
                    }
                }
            })

            tree.addTreeExpansionListener(object : TreeExpansionListener {
                override fun treeExpanded(event: TreeExpansionEvent) {
                    properties.putString("SFTPTabbed.Tree.state", TreeUtils.saveExpansionState(tree))
                }

                override fun treeCollapsed(event: TreeExpansionEvent) {
                    properties.putString("SFTPTabbed.Tree.state", TreeUtils.saveExpansionState(tree))
                }
            })
        }

        override fun dispose() {
            properties.putString("SFTPTabbed.Tree.state", TreeUtils.saveExpansionState(tree))
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        return when (dataKey) {
            SFTPDataProviders.FileSystemViewPanel -> fileSystemPanel as T?
            SFTPDataProviders.CoroutineScope -> coroutineScope as T?
            else -> null
        }
    }

    fun selectHost(host: Host) {
        that.setTabTitle(host.name)
        that.host = host
        that.connect()
    }

    private fun setTabTitle(title: String) {
        val tabbed = SwingUtilities.getAncestorOfClass(JTabbedPane::class.java, that)
        if (tabbed is JTabbedPane) {
            for (i in 0 until tabbed.tabCount) {
                if (tabbed.getComponentAt(i) == that) {
                    tabbed.setTitleAt(i, title)
                    break
                }
            }
        }
    }

}