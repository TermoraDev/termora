package app.termora

import app.termora.database.DatabaseManager
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JPanel

internal class ConnectionSettings : JPanel(BorderLayout()), OptionsPane.Option {
    companion object {
        var unifiedTunnelsOnly: Boolean
            get() = DatabaseManager.getInstance().properties.getString("unifiedTunnelsOnly", "false").toBoolean()
            set(value) = DatabaseManager.getInstance().properties.putString("unifiedTunnelsOnly", value.toString())
        var allowFolderConnections: Boolean
            get() = DatabaseManager.getInstance().properties.getString("allowFolderConnections", "false").toBoolean()
            set(value) = DatabaseManager.getInstance().properties.putString("allowFolderConnections", value.toString())
    }

    init {
        add(JCheckBox(I18n.getString("termora.tunnels.unified-only")).apply {
            isSelected = unifiedTunnelsOnly
            addActionListener { unifiedTunnelsOnly = isSelected }
        }, BorderLayout.CENTER)
        add(JCheckBox(I18n.getString("termora.settings.allow-folder-connections")).apply {
            isSelected = allowFolderConnections
            addActionListener { allowFolderConnections = isSelected }
        }, BorderLayout.NORTH)
    }

    override fun getIcon(isSelected: Boolean) = Icons.server
    override fun getTitle() = I18n.getString("termora.settings.connections")
    override fun getJComponent() = this
}
