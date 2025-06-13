package app.termora

import com.formdev.flatlaf.FlatLaf
import java.awt.Color
import javax.swing.UIManager

open class DynamicColor : Color {
    private var regular: Color?
    private val dark: Color?
    private var colorKey: String? = null
    private val color: Color
        get() {
            val r = regular
            val d = dark
            if (r == null || d == null) {
                try {
                    return UIManager.getColor(colorKey)
                } catch (e: Exception) {
                    TODO("Not yet implemented")
                }
            }
            return if (FlatLaf.isLafDark()) d else r
        }

    constructor(regular: Color, dark: Color) : super(regular.rgb, regular.alpha != 255) {
        this.regular = regular
        this.dark = dark
    }

    companion object {
        val BorderColor = DynamicColor("Component.borderColor")
    }

    constructor(key: String) : super(0) {
        this.regular = null
        this.dark = null
        this.colorKey = key
    }

    override fun getRed(): Int {
        return color.red
    }

    override fun getGreen(): Int {
        return color.green
    }

    override fun getBlue(): Int {
        return color.blue
    }

    override fun getAlpha(): Int {
        return color.alpha
    }

    override fun getRGB(): Int {
        return color.rgb
    }

    override fun brighter(): Color {
        return color.brighter()
    }

    override fun darker(): Color {
        return color.darker()
    }
}
