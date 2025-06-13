package app.termora.plugins.geo

import app.termora.ColorHash
import app.termora.tree.HostTreeNode
import app.termora.tree.MarkerSimpleTreeCellAnnotation
import app.termora.tree.SimpleTreeCellAnnotation
import app.termora.tree.SimpleTreeCellRendererExtension
import java.awt.Color
import javax.swing.JTree

class GeoSimpleTreeCellRendererExtension private constructor() : SimpleTreeCellRendererExtension {
    companion object {
        val instance = GeoSimpleTreeCellRendererExtension()
    }

    private val geo get() = Geo.getInstance()

    override fun createAnnotations(
        tree: JTree,
        value: Any?,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): List<SimpleTreeCellAnnotation> {

        val node = value as HostTreeNode? ?: return emptyList()
        if (node.isFolder) return emptyList()
        val protocol = node.data.protocol
        if ((protocol == "SSH" || protocol == "RDP").not()) return emptyList()

        if (GeoHostTreeShowMoreEnableExtension.instance.isShowMore().not()) return emptyList()
        val country = geo.country(node.data.host) ?: return emptyList()

        val text = "${countryCodeToFlagEmoji(country.isoCode)}${country.name}"
        return listOf(
            MarkerSimpleTreeCellAnnotation(
                text,
                foreground = Color.white,
                background = ColorHash.hash(country.isoCode),
            )
        )
    }

    private fun countryCodeToFlagEmoji(code: String): String {
        if (code.length < 2) return "❓"
        val upper = code.take(2).uppercase()
        val first = Character.codePointAt(upper, 0) - 'A'.code + 0x1F1E6
        val second = Character.codePointAt(upper, 1) - 'A'.code + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    override fun ordered(): Long {
        return 1
    }

}