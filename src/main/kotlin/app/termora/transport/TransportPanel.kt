package app.termora.transport

import app.termora.Disposable
import app.termora.Disposer
import app.termora.DynamicColor
import app.termora.actions.DataProvider
import app.termora.actions.DataProviderSupport
import app.termora.terminal.DataKey
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.File
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JSplitPane

/**
 * 传输面板
 */
class TransportPanel : JPanel(BorderLayout()), Disposable, DataProvider {

    companion object {
        private val log = LoggerFactory.getLogger(TransportPanel::class.java)
    }

    private val dataProviderSupport = DataProviderSupport()

    private val transportManager = TransportManager()
    private val leftFileSystemTabbed = FileSystemTabbed(transportManager, true)
    private val rightFileSystemTabbed = FileSystemTabbed(transportManager, false)
    private val fileTransportPanel = FileTransportPanel(transportManager)

    init {
        initView()
        initEvents()
    }

    private fun initView() {

        Disposer.register(this, transportManager)
        Disposer.register(this, leftFileSystemTabbed)
        Disposer.register(this, rightFileSystemTabbed)
        Disposer.register(this, fileTransportPanel)

        dataProviderSupport.addData(TransportDataProviders.LeftFileSystemTabbed, leftFileSystemTabbed)
        dataProviderSupport.addData(TransportDataProviders.RightFileSystemTabbed, rightFileSystemTabbed)
        dataProviderSupport.addData(TransportDataProviders.TransportManager, transportManager)
        dataProviderSupport.addData(TransportDataProviders.TransportPanel, this)

        leftFileSystemTabbed.border = BorderFactory.createMatteBorder(0, 0, 0, 1, DynamicColor.BorderColor)
        rightFileSystemTabbed.border = BorderFactory.createMatteBorder(0, 1, 0, 0, DynamicColor.BorderColor)


        val splitPane = JSplitPane()
        splitPane.leftComponent = leftFileSystemTabbed
        splitPane.rightComponent = rightFileSystemTabbed
        splitPane.resizeWeight = 0.5
        splitPane.border = BorderFactory.createMatteBorder(0, 0, 1, 0, DynamicColor.BorderColor)
        splitPane.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                removeComponentListener(this)
                splitPane.setDividerLocation(splitPane.resizeWeight)
            }
        })

        fileTransportPanel.border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor)

        val rootSplitPane = JSplitPane()
        rootSplitPane.orientation = JSplitPane.VERTICAL_SPLIT
        rootSplitPane.topComponent = splitPane
        rootSplitPane.bottomComponent = fileTransportPanel
        rootSplitPane.resizeWeight = 0.75
        rootSplitPane.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                removeComponentListener(this)
                rootSplitPane.setDividerLocation(rootSplitPane.resizeWeight)
            }
        })

        add(rootSplitPane, BorderLayout.CENTER)
    }

    @Suppress("DuplicatedCode")
    private fun initEvents() {
        transportManager.addTransportListener(object : TransportListener {
            override fun onTransportAdded(transport: Transport) {
            }

            override fun onTransportRemoved(transport: Transport) {

            }

            override fun onTransportChanged(transport: Transport) {
                if (transport.state == TransportState.Done) {
                    val targetHolder = transport.targetHolder
                    if (targetHolder is FileSystemPanel) {
                        if (transport.target.parent == targetHolder.workdir) {
                            targetHolder.reload()
                        }
                    }
                }
            }

        })


        leftFileSystemTabbed.addFileSystemTransportListener(object : FileSystemTransportListener {
            override fun transport(fileSystemPanel: FileSystemPanel, workdir: Path, isDirectory: Boolean, path: Path) {
                val target = rightFileSystemTabbed.getSelectedFileSystemPanel() ?: return
                transport(
                    fileSystemPanel.workdir, target.workdir,
                    isSourceDirectory = isDirectory,
                    sourcePath = path,
                    sourceHolder = fileSystemPanel,
                    targetHolder = target,
                )
            }
        })


        rightFileSystemTabbed.addFileSystemTransportListener(object : FileSystemTransportListener {
            override fun transport(fileSystemPanel: FileSystemPanel, workdir: Path, isDirectory: Boolean, path: Path) {
                val target = leftFileSystemTabbed.getSelectedFileSystemPanel() ?: return
                transport(
                    fileSystemPanel.workdir, target.workdir,
                    isSourceDirectory = isDirectory,
                    sourcePath = path,
                    sourceHolder = fileSystemPanel,
                    targetHolder = target,
                )
            }
        })
    }

    fun transport(
        sourceWorkdir: Path,
        targetWorkdir: Path,
        isSourceDirectory: Boolean,
        sourcePath: Path,
        sourceHolder: Disposable,
        targetHolder: Disposable
    ) {
        val relativizePath = sourceWorkdir.relativize(sourcePath).toString()
        if (StringUtils.isEmpty(relativizePath) || relativizePath == File.separator ||
            relativizePath == sourceWorkdir.fileSystem.separator ||
            relativizePath == targetWorkdir.fileSystem.separator
        ) {
            return
        }

        val transport: Transport
        if (isSourceDirectory) {
            transport = DirectoryTransport(
                name = sourcePath.fileName.toString(),
                source = sourcePath,
                target = targetWorkdir.resolve(relativizePath),
                sourceHolder = sourceHolder,
                targetHolder = targetHolder,
            )
        } else {
            transport = FileTransport(
                name = sourcePath.fileName.toString(),
                source = sourcePath,
                target = targetWorkdir.resolve(relativizePath),
                sourceHolder = sourceHolder,
                targetHolder = targetHolder,
            )
        }

        transportManager.addTransport(transport)
    }

    override fun dispose() {
        if (log.isInfoEnabled) {
            log.info("Transport is disposed")
        }
    }

    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        if (dataKey == TransportDataProviders.LeftFileSystemPanel ||
            dataKey == TransportDataProviders.RightFileSystemPanel
        ) {
            dataProviderSupport.removeData(dataKey)
            if (dataKey == TransportDataProviders.LeftFileSystemPanel) {
                leftFileSystemTabbed.getSelectedFileSystemPanel()?.let {
                    dataProviderSupport.addData(dataKey, it)
                }
            } else {
                rightFileSystemTabbed.getSelectedFileSystemPanel()?.let {
                    dataProviderSupport.addData(dataKey, it)
                }
            }

        }
        return dataProviderSupport.getData(dataKey)
    }
}