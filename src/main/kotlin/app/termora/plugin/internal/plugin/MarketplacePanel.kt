package app.termora.plugin.internal.plugin

import app.termora.*
import app.termora.plugin.Extension
import app.termora.plugin.Plugin
import app.termora.plugin.PluginManager
import app.termora.plugin.PluginOrigin
import app.termora.plugin.marketplace.MarketplaceManager
import app.termora.plugin.marketplace.MarketplacePlugin
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.jdesktop.swingx.JXBusyLabel
import org.jdesktop.swingx.JXHyperlink
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.ActionEvent
import javax.swing.*


class MarketplacePanel : JPanel(BorderLayout()), Disposable {

    private val pluginsPanel = JPanel(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 8))
    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)
    private val loadingPanel = JPanel(BorderLayout())
    private val fetchFailedPanel = JPanel(BorderLayout())
    private val busyLabel = JXBusyLabel()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val failedLabel = JLabel()

    private val marketplaceManager get() = MarketplaceManager.getInstance()

    private enum class PanelState {
        Loading,
        Plugins,
        FetchFailed,
    }

    init {
        initView()
        initEvents()
    }


    private fun initView() {
        val scrollPane = JScrollPane(pluginsPanel)
        scrollPane.border = BorderFactory.createEmptyBorder()
        scrollPane.verticalScrollBar.unitIncrement = 16
        scrollPane.horizontalScrollBar.unitIncrement = 16
        add(cardPanel, BorderLayout.CENTER)

        cardPanel.add(scrollPane, PanelState.Plugins.name)
        cardPanel.add(loadingPanel, PanelState.Loading.name)
        cardPanel.add(fetchFailedPanel, PanelState.FetchFailed.name)

        busyLabel.isBusy = true
        loadingPanel.border = BorderFactory.createEmptyBorder(60, 0, 0, 0)
        fetchFailedPanel.border = BorderFactory.createEmptyBorder(60, 0, 0, 0)

        run {
            val box = Box.createHorizontalBox()
            box.add(Box.createHorizontalGlue())
            box.add(busyLabel)
            box.add(Box.createHorizontalGlue())
            loadingPanel.add(box, BorderLayout.NORTH)
        }


        run {
            val box1 = Box.createHorizontalBox()
            box1.add(Box.createHorizontalGlue())
            failedLabel.foreground = DynamicColor("textInactiveText")
            box1.add(failedLabel)
            box1.add(Box.createHorizontalGlue())

            val box3 = Box.createVerticalBox()
            box3.add(box1)
            box3.add(Box.createVerticalStrut(8))
            box3.add(createRetry())

            fetchFailedPanel.add(box3, BorderLayout.NORTH)

        }


    }

    private fun initEvents() {
        // 立即加载
        reload()
    }

    private fun reload() {
        coroutineScope.launch {
            withContext(Dispatchers.Swing) {
                cardLayout.show(cardPanel, PanelState.Loading.name)
            }

            // 获取插件
            try {
                val loadedPlugins = PluginManager.getInstance().getLoadedPluginDescriptor()
                val plugins = marketplaceManager.getPlugins()
                    .filterNot { e -> loadedPlugins.any { it.id == e.id && it.version >= e.versions.first().version } }

                withContext(Dispatchers.Swing) {
                    if (plugins.isNotEmpty()) {
                        pluginsPanel.removeAll()
                        for (plugin in plugins) {
                            pluginsPanel.add(createMarketplacePluginPanel(plugin))
                            pluginsPanel.add(JToolBar.Separator())
                        }
                        pluginsPanel.add(createRetry())
                        cardLayout.show(cardPanel, PanelState.Plugins.name)
                    } else {
                        failedLabel.text = "No plugins found"
                        cardLayout.show(cardPanel, PanelState.FetchFailed.name)
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Swing) {
                    failedLabel.text = "Failed to fetch the plugins"
                    cardLayout.show(cardPanel, PanelState.FetchFailed.name)
                }
            }

        }
    }

    private fun createRetry(): JComponent {
        val box = Box.createHorizontalBox()
        box.add(Box.createHorizontalGlue())
        box.add(JXHyperlink(object : AbstractAction(I18n.getString("termora.transport.sftp.retry")) {
            override fun actionPerformed(e: ActionEvent) {
                marketplaceManager.clear()
                reload()
            }
        }).apply { isFocusable = false })
        box.add(Box.createHorizontalGlue())
        return box
    }

    private fun createMarketplacePluginPanel(e: MarketplacePlugin): JPanel {
        val panel = PluginPanel(
            PluginPluginDescriptor(
                plugin = object : Plugin {
                    override fun getAuthor(): String {
                        return e.vendor.name
                    }

                    override fun getName(): String {
                        return e.name
                    }

                    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
                        return emptyList()
                    }

                },
                id = e.id,
                icon = e.icon,
                origin = PluginOrigin.Marketplace,
                version = e.versions.first().version,
                descriptions = e.descriptions,
                downloadUrl = e.versions.first().downloadUrl,
                marketplace = true,
                signature = e.versions.first().signature
            )
        )
        Disposer.register(this, panel)
        return panel
    }


    override fun dispose() {
        busyLabel.isBusy = false
        coroutineScope.cancel()
    }
}