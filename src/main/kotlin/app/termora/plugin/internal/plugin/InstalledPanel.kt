package app.termora.plugin.internal.plugin

import app.termora.Disposable
import app.termora.VerticalFlowLayout
import app.termora.plugin.PluginManager
import app.termora.plugin.PluginOrigin
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JToolBar

class InstalledPanel : JPanel(BorderLayout()), Disposable {

    private val pluginsPanel = object : JPanel(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 8)) {
        override fun remove(index: Int) {
            super.remove(index)
            // 还要移除 JToolBar.Separator
            super.remove(index)
        }
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
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun initEvents() {
        // 立即加载
        reload()
    }

    private fun reload() {
        for (descriptor in PluginManager.getInstance().getLoadedPluginDescriptor()) {
            if (descriptor.origin == PluginOrigin.Internal) continue
            addPluginPanel(
                PluginPanel(
                    PluginPluginDescriptor(
                        plugin = descriptor.plugin,
                        id = descriptor.id,
                        icon = descriptor.icon,
                        origin = descriptor.origin,
                        version = descriptor.version,
                        descriptions = descriptor.descriptions,
                        marketplace = false,
                        path = descriptor.path,
                    )
                )
            )
        }
    }

    fun addPluginPanel(pluginPanel: PluginPanel) {
        pluginsPanel.add(
            pluginPanel
        )
        pluginsPanel.add(JToolBar.Separator())
    }


}