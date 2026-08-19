package app.termora

import com.formdev.flatlaf.extras.components.FlatComboBox
import com.formdev.flatlaf.util.FontUtils
import java.awt.Component
import java.awt.Dimension
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

class FontComboBox : FlatComboBox<String>() {
    private var fontsLoaded = false

    init {
        val fontComboBox = this
        fontComboBox.renderer = object : DefaultListCellRenderer() {
            init {
                preferredSize = Dimension(preferredSize.width, fontComboBox.preferredSize.height - 2)
                maximumSize = Dimension(preferredSize.width, preferredSize.height)
            }

            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                var text = value
                if (text is String) {
                    val fontFamily = text
                    if (text.isBlank()) {
                        text = "&lt;${I18n.getString("termora.settings.terminal.fallback-font.none")}&gt;"
                    }
                    return super.getListCellRendererComponent(
                        list,
                        "<html><font face='$fontFamily'>$text</font></html>",
                        index,
                        isSelected,
                        cellHasFocus
                    )
                }
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            }
        }
        fontComboBox.maximumSize = fontComboBox.preferredSize

        fontComboBox.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
                if (fontsLoaded) return
                val selectedItem = fontComboBox.selectedItem
                val families = getItems()
                for (family in FontUtils.getAvailableFontFamilyNames()) {
                    if (families.contains(family).not()) fontComboBox.addItem(family)
                }
                fontComboBox.selectedItem = selectedItem
                fontsLoaded = true
            }

            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {}
            override fun popupMenuCanceled(e: PopupMenuEvent) {}
        })
    }


    fun getItems(): Set<String> {
        val families = mutableSetOf<String>()
        for (i in 0 until itemCount) families.add(getItemAt(i))
        return families
    }
}
