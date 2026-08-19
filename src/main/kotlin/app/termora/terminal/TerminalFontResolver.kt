package app.termora.terminal

import java.awt.Font

object TerminalFontResolver {
    private val preferredFallbackFamilies = listOf(
        "Cascadia Mono",
        "Cascadia Code",
        "Consolas",
        "Lucida Console",
        Font.MONOSPACED,
    )

    fun resolve(fontName: String, style: Int, size: Int): Font {
        val requestedFont = Font(fontName, style, size)
        return if (isExactFamily(requestedFont, fontName)) {
            requestedFont
        } else {
            fallback(style, size)
        }
    }

    fun fallback(style: Int, size: Int): Font {
        for (family in preferredFallbackFamilies) {
            val font = Font(family, style, size)
            if (isExactFamily(font, family)) {
                return font
            }
        }
        return Font(Font.MONOSPACED, style, size)
    }

    private fun isExactFamily(font: Font, requestedFamily: String): Boolean {
        return font.family.equals(requestedFamily, ignoreCase = true)
    }
}
