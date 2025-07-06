package app.termora.transfer

import app.termora.Disposable
import app.termora.Disposer
import app.termora.DynamicColor
import app.termora.Icons
import app.termora.actions.DataProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.nio.file.Path
import javax.swing.*
import kotlin.time.Duration.Companion.milliseconds


internal class TransportViewer : JPanel(BorderLayout()), DataProvider, Disposable {

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val splitPane = JSplitPane()
    private val transferManager = TransferTableModel(coroutineScope)
    private val transferTable = TransferTable(coroutineScope, transferManager)
    private val leftTabbed = TransportTabbed(transferManager)
    private val rightTabbed = TransportTabbed(transferManager)
    private val leftTransferManager = createInternalTransferManager(leftTabbed, rightTabbed)
    private val rightTransferManager = createInternalTransferManager(rightTabbed, leftTabbed)
    private val rootSplitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT)
    private val owner get() = SwingUtilities.getWindowAncestor(this)

    init {
        initView()
        initEvents()
    }

    private fun initView() {
        isFocusable = false

        leftTabbed.internalTransferManager = leftTransferManager
        rightTabbed.internalTransferManager = rightTransferManager

        leftTabbed.addLocalTab()
        rightTabbed.addSelectionTab()

        val scrollPane = JScrollPane(transferTable)
        scrollPane.border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor)

        leftTabbed.border = BorderFactory.createMatteBorder(0, 0, 0, 1, DynamicColor.BorderColor)
        rightTabbed.border = BorderFactory.createMatteBorder(0, 1, 0, 0, DynamicColor.BorderColor)

        splitPane.resizeWeight = 0.5
        splitPane.leftComponent = leftTabbed
        splitPane.rightComponent = rightTabbed
        splitPane.border = BorderFactory.createMatteBorder(0, 0, 1, 0, DynamicColor.BorderColor)

        rootSplitPane.resizeWeight = 0.7
        rootSplitPane.topComponent = splitPane
        rootSplitPane.bottomComponent = scrollPane

        add(rootSplitPane, BorderLayout.CENTER)
    }

    private fun initEvents() {
        splitPane.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                removeComponentListener(this)
                splitPane.setDividerLocation(splitPane.resizeWeight)
            }
        })

        rootSplitPane.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                removeComponentListener(this)
                rootSplitPane.setDividerLocation(rootSplitPane.resizeWeight)
            }
        })


        coroutineScope.launch(Dispatchers.Swing) {
            while (isActive) {
                delay(250.milliseconds)
                checkDisconnected(leftTabbed)
                checkDisconnected(rightTabbed)
            }
        }

        Disposer.register(this, leftTabbed)
        Disposer.register(this, rightTabbed)
    }

    private fun checkDisconnected(tabbed: TransportTabbed) {
        for (i in 0 until tabbed.tabCount) {
            val tab = tabbed.getTransportPanel(i) ?: continue
            val icon = tabbed.getIconAt(i)
            if (tab.loader.isOpened()) {
                if (icon == null) continue
                tabbed.setIconAt(i, null)
            } else {
                if (icon == Icons.breakpoint) continue
                tabbed.setIconAt(i, Icons.breakpoint)
            }
        }
    }

    private fun createInternalTransferManager(
        source: TransportTabbed,
        target: TransportTabbed
    ): InternalTransferManager {
        return DefaultInternalTransferManager(
            { owner },
            coroutineScope,
            transferManager,
            object : DefaultInternalTransferManager.WorkdirProvider {
                override fun getWorkdir(): Path? {
                    return source.getSelectedTransportPanel()?.workdir
                }


            },
            object : DefaultInternalTransferManager.WorkdirProvider {
                override fun getWorkdir(): Path? {
                    return target.getSelectedTransportPanel()?.workdir
                }

            })

    }

    fun getTransferManager(): TransferManager {
        return transferManager
    }

    fun getLeftTabbed(): TransportTabbed {
        return leftTabbed
    }

    fun getRightTabbed(): TransportTabbed {
        return rightTabbed
    }

    override fun dispose() {
        coroutineScope.cancel()
    }

}