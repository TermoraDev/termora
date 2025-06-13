package app.termora

import org.apache.commons.codec.digest.MurmurHash3
import java.awt.Color
import kotlin.math.absoluteValue

object ColorHash {
    fun hash(text: String): Color {
        val hash = MurmurHash3.hash32x86(text.toByteArray())

        val r = (hash shr 16) and 0xFF
        val g = (hash shr 8) and 0xFF
        val b = hash and 0xFF

        val color = Color(r, g, b)

        return color.darker()
    }
}