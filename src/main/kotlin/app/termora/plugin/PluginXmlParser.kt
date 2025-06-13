package app.termora.plugin

import app.termora.Application
import app.termora.Icons
import app.termora.plugin.internal.plugin.PluginSVGIcon
import org.apache.commons.lang3.StringUtils
import org.semver4j.Semver
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.InputStream
import javax.swing.Icon
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

internal object PluginXmlParser {
    private val xPath = XPathFactory.newInstance().newXPath()
    private val documentBuilder by lazy {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isValidating = false
        factory.isXIncludeAware = false
        factory.isNamespaceAware = false
        factory.newDocumentBuilder()
    }

    fun parse(input: InputStream): Document {
        return documentBuilder.parse(InputSource(input))
    }

    fun parse(plugin: InputStream, icon: InputStream?, darkIcon: InputStream?): MyPluginDescriptor {

        val version = Semver.parse(Application.getVersion())
            ?: throw IllegalStateException("illegal version ${Application.getVersion()}")
        val document = parse(plugin)
        val root = document.documentElement
        if (root.tagName != "termora-plugin") throw IllegalStateException()

        // 存在 paid 标签则需要订阅
        val paid = xPath.compile("/termora-plugin/paid")
            .evaluate(document, XPathConstants.NODE) != null

        val pluginVersion = xPath.compile("/termora-plugin/version/text()").evaluate(document)
        val pluginId = xPath.compile("/termora-plugin/id/text()").evaluate(document)
        val pluginName = xPath.compile("/termora-plugin/name/text()").evaluate(document)
        val pluginEntry = xPath.compile("/termora-plugin/entry/text()").evaluate(document)
        val since = xPath.compile("/termora-plugin/termora-version/@since").evaluate(document)
        val until = xPath.compile("/termora-plugin/termora-version/@until").evaluate(document)
        if (StringUtils.isAnyBlank(
                pluginVersion,
                pluginName,
                pluginId,
                pluginEntry,
                since
            )
        ) throw IllegalStateException("illegal plugin")

        // 版本要求不匹配
        if (version.satisfies(since).not()) throw IllegalStateException("version mismatch")
        if (until.isNullOrBlank().not() && version.satisfies(until)
                .not()
        ) throw IllegalStateException("version mismatch")

        // 获取描述
        val descriptionNodeset = xPath.compile("/termora-plugin/descriptions/description")
            .evaluate(document, XPathConstants.NODESET)
        val descriptions = mutableListOf<PluginDescription>()
        if (descriptionNodeset is NodeList) {
            for (i in 1..descriptionNodeset.length) {
                val language =
                    xPath.compile("/termora-plugin/descriptions/description[${i}]/@language")
                        .evaluate(document)
                val description =
                    xPath.compile("/termora-plugin/descriptions/description[${i}]/text()")
                        .evaluate(document)
                descriptions.add(PluginDescription(language, description))
            }
        }

        var myIcon: Icon = Icons.plugin
        if (icon != null && darkIcon != null) {
            myIcon = PluginSVGIcon(icon, darkIcon)
        } else if (icon != null) {
            myIcon = PluginSVGIcon(icon)
        }

        return MyPluginDescriptor(
            plugin = object : Plugin {
                override fun getAuthor(): String {
                    return StringUtils.EMPTY
                }

                override fun getName(): String {
                    return pluginName
                }

                override fun isPaid(): Boolean {
                    return paid
                }

                override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
                    return emptyList()
                }
            },
            id = pluginId,
            icon = myIcon,
            origin = PluginOrigin.Memory,
            version = Semver.parse(pluginVersion) ?: throw IllegalStateException("illegal version $pluginVersion"),
            descriptions = descriptions,
            entry = pluginEntry,
        )
    }

    internal class MyPluginDescriptor(
        plugin: Plugin,
        id: String = plugin.getName(),
        icon: Icon = defaultIcon,
        origin: PluginOrigin,
        version: Semver,
        descriptions: List<PluginDescription> = emptyList(),
        val entry: String,
    ) : PluginDescriptor(
        plugin = plugin,
        id = id,
        icon = icon,
        origin = origin,
        version = version,
        descriptions = descriptions,
        path = null,
    )
}