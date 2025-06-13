package app.termora.plugin.marketplace

import app.termora.plugin.PluginDescription
import org.semver4j.Semver
import javax.swing.Icon

data class MarketplacePlugin(
    val id: String,
    val name: String,
    val paid: Boolean,
    val icon: Icon,
    val versions: MutableList<MarketplacePluginVersion>,
    val descriptions: MutableList<PluginDescription>,
    val vendor: PluginVendor
)

class MarketplacePluginVersion(
    val version: Semver,
    val since: String,
    val until: String,
    val downloadUrl: String,
    val signature:String,
)


data class PluginVendor(
    val name: String,
    val url: String
)