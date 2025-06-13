package app.termora

import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon
import javax.swing.UIManager

class CheckBoxMenuItemColorIcon(
    private val colorIcon: ColorIcon,
    private val selected: Boolean,
) : Icon {
    private val checkIcon = UIManager.getIcon("CheckBoxMenuItem.checkIcon")

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        if (selected) {
            checkIcon.paintIcon(c, g, x, y)
        }
        colorIcon.paintIcon(c, g, x + checkIcon.iconWidth + 6, y)
    }

    override fun getIconWidth(): Int {
        return colorIcon.iconWidth + checkIcon.iconWidth + 6
    }

    override fun getIconHeight(): Int {
        return colorIcon.iconHeight
    }

}