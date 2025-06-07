package app.termora

import com.formdev.flatlaf.FlatLaf
import java.awt.*
import javax.swing.*
import javax.swing.border.Border


open class OptionsPane : JPanel(BorderLayout()) {
    protected val formMargin = "7dlu"

    protected val tabListModel = DefaultListModel<Option>()
    protected val tabList = object : JList<Option>(tabListModel) {
        override fun getBackground(): Color {
            return this@OptionsPane.background
        }
    }
    private val cardLayout = CardLayout()
    private val contentPanel = JPanel(cardLayout)
    private val loadedComponents = mutableMapOf<String, JComponent>()

    init {
        initView()
        initEvents()
    }

    private fun initView() {

        tabList.fixedCellHeight = (UIManager.getInt("Tree.rowHeight") * 1.2).toInt()
        tabList.fixedCellWidth = 170
        tabList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        tabList.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, DynamicColor.BorderColor),
            BorderFactory.createEmptyBorder(6, 6, 0, 6)
        )
        tabList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val option = value as Option
                val c = super.getListCellRendererComponent(list, option.getTitle(), index, isSelected, cellHasFocus)

                icon = option.getIcon(isSelected)
                if (isSelected && tabList.hasFocus()) {
                    if (!FlatLaf.isLafDark()) {
                        if (icon is DynamicIcon) {
                            icon = (icon as DynamicIcon).dark
                        }
                    }
                }

                return c
            }
        }


        add(tabList, BorderLayout.WEST)
        add(contentPanel, BorderLayout.CENTER)
    }

    fun selectOption(option: Option) {
        val index = tabListModel.indexOf(option)
        if (index < 0) {
            return
        }
        setSelectedIndex(index)
    }

    fun getSelectedOption(): Option? {
        val index = tabList.selectedIndex
        if (index < 0) return null
        return tabListModel.getElementAt(index)
    }

    fun getSelectedIndex(): Int {
        return tabList.selectedIndex
    }

    fun setSelectedIndex(index: Int) {
        tabList.selectedIndex = index
    }

    fun selectOptionJComponent(c: JComponent) {
        for (element in tabListModel.elements()) {
            var p = c as Container?
            while (p != null) {
                if (p == element) {
                    selectOption(element)
                    return
                }
                p = p.parent
            }
        }
    }


    fun addOption(option: Option) {
        for (element in tabListModel.elements()) {
            if (element.getTitle() == option.getTitle()) {
                throw UnsupportedOperationException("Title already exists")
            }
        }
        tabListModel.addElement(option)

        if (tabList.selectedIndex < 0) {
            tabList.selectedIndex = 0
        }
    }

    fun removeOption(option: Option) {
        val title = option.getTitle()
        loadedComponents[title]?.let {
            contentPanel.remove(it)
            loadedComponents.remove(title)
        }
        tabListModel.removeElement(option)
    }

    fun setContentBorder(border: Border) {
        contentPanel.border = border
    }

    private fun initEvents() {
        tabList.addListSelectionListener {
            if (tabList.selectedIndex >= 0) {
                val option = tabListModel.get(tabList.selectedIndex)
                val title = option.getTitle()

                if (!loadedComponents.containsKey(title)) {
                    val component = option.getJComponent()
                    loadedComponents[title] = component
                    contentPanel.add(component, title)
                    SwingUtilities.updateComponentTreeUI(component)
                }

                cardLayout.show(contentPanel, title)
            }
        }
    }

    interface Option {
        fun getIcon(isSelected: Boolean): Icon
        fun getTitle(): String
        fun getJComponent(): JComponent
    }
}