package app.termora.plugin

import app.termora.I18n
import app.termora.Icons
import com.formdev.flatlaf.extras.FlatSVGIcon
import org.semver4j.Semver
import java.io.File
import java.util.*
import javax.swing.Icon

open class PluginDescriptor(
    val plugin: Plugin,
    val id: String = plugin.getName(),
    val icon: Icon = defaultIcon,
    val origin: PluginOrigin,
    val version: Semver,
    val descriptions: List<PluginDescription> = emptyList(),
    val path: File? = null,
) {
    companion object {
        val defaultIcon: Icon = FlatSVGIcon(Icons.plugin.name, 32, 32)
    }

    val description: String get() = getBestDescription()

    private fun getBestDescription(): String {
        if (descriptions.isEmpty()) return plugin.getName()
        if (descriptions.size == 1) return descriptions.first().text

        val language = I18n.containsLanguage(Locale.getDefault()) ?: "en_US"
        val first = descriptions.firstOrNull { it.language == language }
        if (first != null) {
            return first.text
        }

        return descriptions.first().text
    }
}


data class PluginDescription(
    val language: String,
    val text: String,
)