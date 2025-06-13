package app.termora.plugin.internal.plugin

import app.termora.plugin.Plugin
import app.termora.plugin.PluginDescription
import app.termora.plugin.PluginDescriptor
import app.termora.plugin.PluginOrigin
import org.apache.commons.lang3.StringUtils
import org.semver4j.Semver
import java.io.File
import javax.swing.Icon

class PluginPluginDescriptor(
    plugin: Plugin,
    id: String = plugin.getName(),
    icon: Icon = defaultIcon,
    origin: PluginOrigin,
    version: Semver,
    descriptions: List<PluginDescription> = emptyList(),
    val downloadUrl: String = StringUtils.EMPTY,
    val marketplace: Boolean = false,
    val signature: String = StringUtils.EMPTY,
    path: File? = null
) : PluginDescriptor(plugin, id, icon, origin, version, descriptions, path)