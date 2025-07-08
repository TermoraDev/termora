package app.termora.transfer

import app.termora.Disposable
import app.termora.Disposer
import app.termora.DynamicColor
import app.termora.actions.DataProvider
import app.termora.actions.DataProviderSupport
import app.termora.terminal.DataKey
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.nio.file.Path
import javax.swing.*
import kotlin.time.Duration.Companion.milliseconds


internal class TransportViewer : JPanel(BorderLayout()), DataProvider, Disposable {
    companion object {
        val MyTransferManager = DataKey(TransferManager::class)
    }

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
    private val support = DataProviderSupport()

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

        support.addData(MyTransferManager, transferManager)

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
                checkDisconnected(leftTabbed)
                checkDisconnected(rightTabbed)
                delay(250.milliseconds)
            }
        }

        Disposer.register(this, transferManager)
        Disposer.register(this, transferTable)
        Disposer.register(this, leftTabbed)
        Disposer.register(this, rightTabbed)
    }

    private fun checkDisconnected(tabbed: TransportTabbed) {
        for (i in 0 until tabbed.tabCount) {
            val tab = tabbed.getTransportPanel(i) ?: continue
            val icon = tabbed.getIconAt(i)
            if (tab.loader.isOpened()) {
                if (icon != MyIcon.Success) {
                    tabbed.setIconAt(i, MyIcon.Success)
                }
            } else if (tab.loader.isOpening()) {
                if (icon != MyIcon.Warning) {
                    tabbed.setIconAt(i, MyIcon.Warning)
                }
            } else {
                if (icon != MyIcon.Error) {
                    tabbed.setIconAt(i, MyIcon.Error)
                }
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

    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        return support.getData(dataKey)
    }

    internal class MyIcon(private val color: Color) : Icon {
        private val size = 10


        // https://plugins.jetbrains.com/docs/intellij/icons-style.html#action-icons
        companion object {
            val Success = MyIcon(DynamicColor(Color(89, 168, 105), Color(73, 156, 84)))
            val Error = MyIcon(DynamicColor(Color(219, 88, 96), Color(199, 84, 80)))
            val Warning = MyIcon(DynamicColor(Color(237, 162, 0), Color(240, 167, 50)))
        }

        override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
            if (g is Graphics2D) {
                g.color = color
                val centerX = x + (iconWidth - size) / 2
                val centerY = y + (iconHeight - size) / 2
                g.fillRoundRect(centerX, centerY, size, size, size, size)
            }
        }

        override fun getIconWidth(): Int {
            return 16
        }

        override fun getIconHeight(): Int {
            return 16
        }

    }

}