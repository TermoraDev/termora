package app.termora.transfer

import app.termora.*
import app.termora.database.DatabaseManager
import app.termora.protocol.TransferProtocolProvider
import app.termora.tree.*
import com.formdev.flatlaf.icons.FlatOptionPaneErrorIcon
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.jdesktop.swingx.JXBusyLabel
import org.jdesktop.swingx.JXHyperlink
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.Executors
import javax.swing.*
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener

internal class TransportSelectionPanel(
    private val tabbed: TransportTabbed,
    private val transferManager: InternalTransferManager,
) : JPanel(BorderLayout()), Disposable {

    companion object {
        private val log = LoggerFactory.getLogger(TransportSelectionPanel::class.java)
    }

    enum class State {
        Initialized,
        Connecting,
        ConnectFailed,
    }

    var state = State.Initialized
        private set
    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)
    private val connectingPanel = ConnectingPanel()
    private val selectHostPanel = SelectHostPanel()
    private val connectFailedPanel = ConnectFailedPanel()
    private val properties get() = DatabaseManager.getInstance().properties
    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val executorService = Executors.newVirtualThreadPerTaskExecutor()
    private val coroutineDispatcher = executorService.asCoroutineDispatcher()
    private val coroutineScope = CoroutineScope(coroutineDispatcher)

    private val that get() = this
    private var host: Host? = null

    init {
        initView()
        initEvents()
    }

    private fun initView() {
        isFocusable = false
        cardPanel.add(selectHostPanel, State.Initialized.name)
        cardPanel.add(connectingPanel, State.Connecting.name)
        cardPanel.add(connectFailedPanel, State.ConnectFailed.name)
        cardLayout.show(cardPanel, State.Initialized.name)
        add(cardPanel, BorderLayout.CENTER)
    }

    private fun initEvents() {
        Disposer.register(this, selectHostPanel)
    }

    fun connect(host: Host) {
        if (state == State.Connecting) return
        state = State.Connecting
        this.host = host

        connectingPanel.busyLabel.isBusy = true
        cardLayout.show(cardPanel, State.Connecting.name)

        coroutineScope.launch {
            try {
                doConnect(host)
            } catch (e: Exception) {
                if (log.isErrorEnabled) log.error(e.message, e)
                withContext(Dispatchers.Swing) {
                    state = State.ConnectFailed
                    connectFailedPanel.errorLabel.text = ExceptionUtils.getRootCauseMessage(e)
                    cardLayout.show(cardPanel, State.ConnectFailed.name)
                }
            }
        }.invokeOnCompletion { swingCoroutineScope.launch { connectingPanel.busyLabel.isBusy = false } }
    }

    private suspend fun doConnect(host: Host) {

        val loader = ReconnectableTransportSupportLoader(owner, host)

        // try load
        loader.getTransportSupport()

        withContext(Dispatchers.Swing) {
            val panel = TransportPanel(transferManager, host, loader)
            Disposer.register(panel, object : Disposable {
                override fun dispose() {
                    Disposer.dispose(loader)
                }
            })
            swingCoroutineScope.launch {
                tabbed.remove(that)
                tabbed.addTab(host.name, panel)
                tabbed.selectedIndex = tabbed.tabCount - 1
            }
        }
    }

    override fun requestFocusInWindow(): Boolean {
        return selectHostPanel.tree.requestFocusInWindow()
    }

    override fun dispose() {
        coroutineScope.cancel()
        coroutineDispatcher.close()
        executorService.shutdownNow()
        connectingPanel.busyLabel.isBusy = false
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
                "40dlu, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
            )

            errorLabel.horizontalAlignment = SwingConstants.CENTER

            val builder = FormBuilder.create().layout(layout).debug(false)
            builder.add(FlatOptionPaneErrorIcon()).xy(2, 2)
            builder.add(errorLabel).xyw(1, 4, 3, "fill, center")
            builder.add(JXHyperlink(object : AbstractAction(I18n.getString("termora.transport.sftp.retry")) {
                override fun actionPerformed(e: ActionEvent) {
                    host?.let { connect(it) }
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
                    cardLayout.show(cardPanel, State.Initialized.name)
                    selectHostPanel.tree.requestFocusInWindow()
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
        val tree = NewHostTree()

        init {
            initView()
            initEvents()
        }

        private fun initView() {
            tree.contextmenu = false
            tree.dragEnabled = false
            tree.isRootVisible = false
            tree.doubleClickConnection = false
            tree.showsRootHandles = true

            val scrollPane = JScrollPane(tree)
            scrollPane.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            add(scrollPane, BorderLayout.CENTER)

            val filterableTreeModel = FilterableTreeModel(tree)
            filterableTreeModel.addFilter(object : Filter {
                override fun filter(node: Any): Boolean {
                    if (node !is HostTreeNode) return false
                    return TransferProtocolProvider.valueOf(node.host.protocol) != null
                }
            })
            filterableTreeModel.filter()
            tree.model = filterableTreeModel
            Disposer.register(tree, filterableTreeModel)

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
                        connect(host)
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

    }


}