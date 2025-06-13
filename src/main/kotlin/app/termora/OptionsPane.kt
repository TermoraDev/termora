package app.termora

import app.termora.plugin.internal.extension.DynamicExtensionHandler
import com.formdev.flatlaf.FlatLaf
import java.awt.*
import javax.swing.*
import javax.swing.border.Border


abstract class OptionsPane : JPanel(BorderLayout()), Disposable {
    companion object {
        const val FORM_MARGIN = "7dlu"
    }

    private val options = mutableListOf<Option>()
    protected val tabListModel = DefaultListModel<Option>()
    protected val tabList = object : JList<Option>(tabListModel) {
        override fun getBackground(): Color {
            return this@OptionsPane.background
        }
    }
    private val cardLayout = CardLayout()
    private val contentPanel = JPanel(cardLayout)
    private val loadedComponents = mutableMapOf<String, JComponent>()
    private var contentPanelBorder = BorderFactory.createEmptyBorder(6, 8, 6, 8)
    private var themeChanged = false

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


    open fun addOption(option: Option) {
        for (element in tabListModel.elements()) {
            if (element.getTitle() == option.getTitle()) {
                throw UnsupportedOperationException("Title already exists")
            }
        }

        options.add(option)
        contentPanel.add(option.getJComponent(), option.getTitle())

        tabListModel.clear()
        for (e in OptionSorter.sortOptions(options)) {
            tabListModel.addElement(e)
        }

        if (tabList.selectedIndex < 0) {
            setSelectedIndex(0)
        }

        if (option is Disposable) {
            Disposer.register(this, option)
        }

    }

    fun setContentBorder(border: Border) {
        contentPanelBorder = border
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
                    if (themeChanged) SwingUtilities.updateComponentTreeUI(component)
                }

                val contentPanelBorder = option.getJComponent().getClientProperty("ContentPanelBorder")
                if (contentPanelBorder is Border) {
                    contentPanel.border = contentPanelBorder
                } else {
                    contentPanel.border = this.contentPanelBorder
                }

                cardLayout.show(contentPanel, title)
            }
        }

        tabList.addListSelectionListener {
            val index = tabList.selectedIndex
            if (index >= 0) {
                // 选中事件
                tabListModel.getElementAt(index).onSelected()
            }
        }

        // 监听主题变化
        DynamicExtensionHandler.getInstance().register(ThemeChangeExtension::class.java, object : ThemeChangeExtension {
            override fun onChanged() {
                themeChanged = true
            }
        }).let { Disposer.register(this, it) }
    }


    interface Option {
        fun getIcon(isSelected: Boolean): Icon
        fun getTitle(): String
        fun getJComponent(): JComponent
        fun getIdentifier(): String = javaClass.name
        fun getAnchor(): Anchor = Anchor.Null
        fun onSelected() {}
    }

    interface PluginOption : Option {
        override fun getAnchor(): Anchor = Anchor.After("Plugin")
    }


    sealed class Anchor {
        object Null : Anchor()
        object First : Anchor()
        object Last : Anchor()
        data class Before(val target: String) : Anchor()
        data class After(val target: String) : Anchor()
    }

    private object OptionSorter {


        fun sortOptions(options: List<Option>): List<Option> {
            val firsts = options.filter { it.getAnchor() is Anchor.First }
            val lasts = options.filter { it.getAnchor() is Anchor.Last }
            val nulls = options.filter { it.getAnchor() is Anchor.Null }
            val pendingOptions = mutableListOf<Option>()

            val result = mutableListOf<Option>()
            result.addAll(firsts)
            result.addAll(nulls)
            result.addAll(lasts)

            // 首次排序
            sort(options, pendingOptions, result)

            // 对于没有找到对应依赖关系，则在最近的一个 Last 前面
            if (pendingOptions.isNotEmpty()) {
                for (i in 0 until result.size) {
                    if (result[i].getAnchor() is Anchor.Last || i == result.size - 1) {
                        for (n in 0 until pendingOptions.size) {
                            result.add(i + n, pendingOptions[n])
                        }
                        break
                    }
                }
            }


            return result
        }

        private fun sort(
            options: List<Option>,
            pendingOptions: MutableList<Option>,
            result: MutableList<Option>
        ) {
            for (option in options.filter { it.getAnchor() is Anchor.Before || it.getAnchor() is Anchor.After }) {
                val anchor = option.getAnchor()
                if (anchor is Anchor.Before) {
                    val index = findIndex(anchor.target, result)
                    if (index == -1) {
                        pendingOptions.add(option)
                        continue
                    } else {
                        result.add(index, option)
                    }
                } else if (anchor is Anchor.After) {
                    val index = findIndex(anchor.target, result)
                    if (index == -1) {
                        pendingOptions.add(option)
                        continue
                    } else {
                        result.add(index + 1, option)
                    }
                }
            }
        }

        private fun findIndex(identifier: String, list: List<Option>): Int {
            return list.indexOfFirst { it.getIdentifier() == identifier }
        }

    }
}