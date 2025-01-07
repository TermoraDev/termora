package app.termora

import app.termora.findeverywhere.BasicFilterFindEverywhereProvider
import app.termora.findeverywhere.FindEverywhere
import app.termora.findeverywhere.FindEverywhereProvider
import app.termora.findeverywhere.FindEverywhereResult
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.action.ActionContainerFactory
import org.jdesktop.swingx.action.ActionManager
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.*
import javax.swing.JTabbedPane.SCROLL_TAB_LAYOUT
import kotlin.math.min

class TerminalTabbed(
    private val toolbar: JToolBar,
    private val tabbedPane: FlatTabbedPane,
) : JPanel(BorderLayout()), Disposable, TerminalTabbedManager {
    private val tabs = mutableListOf<TerminalTab>()

    private val iconListener = PropertyChangeListener { e ->
        val source = e.source
        if (e.propertyName == "icon" && source is TerminalTab) {
            val index = tabs.indexOf(source)
            if (index >= 0) {
                tabbedPane.setIconAt(index, source.getIcon())
            }
        }
    }


    init {
        initView()
        initEvents()
    }

    private fun initView() {
        tabbedPane.tabLayoutPolicy = SCROLL_TAB_LAYOUT
        tabbedPane.isTabsClosable = true
        tabbedPane.tabType = FlatTabbedPane.TabType.card

        tabbedPane.styleMap = mapOf(
            "focusColor" to UIManager.getColor("TabbedPane.selectedBackground")
        )

        val actionManager = ActionManager.getInstance()
        val actionContainerFactory = ActionContainerFactory(actionManager)
        val updateBtn = actionContainerFactory.createButton(actionManager.getAction(Actions.APP_UPDATE))
        updateBtn.isVisible = updateBtn.isEnabled
        updateBtn.addChangeListener { updateBtn.isVisible = updateBtn.isEnabled }

        toolbar.add(actionContainerFactory.createButton(object : AnAction(StringUtils.EMPTY, Icons.add) {
            override fun actionPerformed(e: ActionEvent?) {
                actionManager.getAction(Actions.FIND_EVERYWHERE)?.actionPerformed(e)
            }

            override fun isEnabled(): Boolean {
                return actionManager.getAction(Actions.FIND_EVERYWHERE)?.isEnabled ?: false
            }
        }))
        toolbar.add(Box.createHorizontalStrut(UIManager.getInt("TabbedPane.tabHeight")))
        toolbar.add(Box.createHorizontalGlue())
        toolbar.add(actionContainerFactory.createButton(actionManager.getAction(Actions.TERMINAL_LOGGER)))
        toolbar.add(actionContainerFactory.createButton(actionManager.getAction(Actions.MACRO)))
        toolbar.add(actionContainerFactory.createButton(actionManager.getAction(Actions.KEYWORD_HIGHLIGHT_EVERYWHERE)))
        toolbar.add(actionContainerFactory.createButton(actionManager.getAction(Actions.KEY_MANAGER)))
        toolbar.add(actionContainerFactory.createButton(actionManager.getAction(Actions.MULTIPLE)))
        toolbar.add(updateBtn)
        toolbar.add(actionContainerFactory.createButton(actionManager.getAction(Actions.FIND_EVERYWHERE)))
        toolbar.add(actionContainerFactory.createButton(actionManager.getAction(Actions.SETTING)))


        tabbedPane.trailingComponent = toolbar

        add(tabbedPane, BorderLayout.CENTER)

    }


    private fun initEvents() {
        // 关闭 tab
        tabbedPane.setTabCloseCallback { _, i -> removeTabAt(i, true) }

        // 选中变动
        tabbedPane.addPropertyChangeListener("selectedIndex", object : PropertyChangeListener {
            override fun propertyChange(evt: PropertyChangeEvent) {
                val oldIndex = evt.oldValue as Int
                val newIndex = evt.newValue as Int
                if (oldIndex >= 0 && tabs.size > newIndex) {
                    tabs[oldIndex].onLostFocus()
                }
                if (newIndex >= 0 && tabs.size > newIndex) {
                    tabs[newIndex].onGrabFocus()
                }
            }
        })

        // 选择变动
        tabbedPane.addChangeListener {
            if (tabbedPane.selectedIndex >= 0) {
                val c = tabbedPane.getComponentAt(tabbedPane.selectedIndex)
                c.requestFocusInWindow()
            }
        }


        // 快捷键
        val inputMap = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        for (i in KeyEvent.VK_1..KeyEvent.VK_9) {
            val tabIndex = i - KeyEvent.VK_1 + 1
            val actionKey = "select_$tabIndex"
            actionMap.put(actionKey, object : AnAction() {
                override fun actionPerformed(e: ActionEvent) {
                    tabbedPane.selectedIndex = if (i == KeyEvent.VK_9 || tabIndex > tabbedPane.tabCount) {
                        tabbedPane.tabCount - 1
                    } else {
                        tabIndex - 1
                    }
                }
            })
            inputMap.put(KeyStroke.getKeyStroke(i, toolkit.menuShortcutKeyMaskEx), actionKey)
        }

        // 关闭 tab
        actionMap.put("closeTab", object : AnAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (tabbedPane.selectedIndex >= 0) {
                    tabbedPane.tabCloseCallback?.accept(tabbedPane, tabbedPane.selectedIndex)
                }
            }
        })
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, toolkit.menuShortcutKeyMaskEx), "closeTab")


        // 右键菜单
        tabbedPane.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!SwingUtilities.isRightMouseButton(e)) {
                    return
                }

                val index = tabbedPane.indexAtLocation(e.x, e.y)
                if (index < 0) return

                showContextMenu(index, e)
            }
        })


        // 点击
        tabbedPane.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    val index = tabbedPane.indexAtLocation(e.x, e.y)
                    if (index > 0) {
                        tabbedPane.getComponentAt(index).requestFocusInWindow()
                    }
                }
            }
        })

        // 注册全局搜索
        FindEverywhere.registerProvider(BasicFilterFindEverywhereProvider(object : FindEverywhereProvider {
            override fun find(pattern: String): List<FindEverywhereResult> {
                val results = mutableListOf<FindEverywhereResult>()
                for (i in 0 until tabbedPane.tabCount) {
                    if (tabbedPane.getComponentAt(i) is WelcomePanel) {
                        continue
                    }
                    results.add(
                        SwitchFindEverywhereResult(
                            tabbedPane.getTitleAt(i),
                            tabbedPane.getIconAt(i),
                            tabbedPane.getComponentAt(i)
                        )
                    )
                }
                return results
            }

            override fun group(): String {
                return I18n.getString("termora.find-everywhere.groups.opened-hosts")
            }

            override fun order(): Int {
                return Integer.MIN_VALUE + 1
            }
        }))


        // 打开 Host
        ActionManager.getInstance().addAction(Actions.OPEN_HOST, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (e !is OpenHostActionEvent) {
                    return
                }
                openHost(e.host)
            }
        })

    }

    private fun removeTabAt(index: Int, disposable: Boolean = true) {
        if (tabbedPane.isTabClosable(index)) {
            val tab = tabs[index]

            if (disposable) {
                if (!tab.canClose()) {
                    return
                }
            }

            tab.onLostFocus()
            tab.removePropertyChangeListener(iconListener)

            // remove tab
            tabbedPane.removeTabAt(index)

            // remove ele
            tabs.removeAt(index)

            // 新的获取到焦点
            tabs[tabbedPane.selectedIndex].onGrabFocus()

            if (disposable) {
                Disposer.dispose(tab)
            }
        }
    }


    private fun openHost(host: Host) {
        val tab = if (host.protocol == Protocol.SSH) SSHTerminalTab(host) else LocalTerminalTab(host)
        addTab(tab)
        tab.start()
    }


    private fun showContextMenu(tabIndex: Int, e: MouseEvent) {
        val c = tabbedPane.getComponentAt(tabIndex) as JComponent
        val tab = tabs[tabIndex]

        val popupMenu = FlatPopupMenu()

        // 修改名称
        val rename = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.rename"))
        rename.addActionListener {
            val index = tabbedPane.selectedIndex
            if (index > 0) {
                val dialog = InputDialog(
                    SwingUtilities.getWindowAncestor(this),
                    title = rename.text,
                    text = tabbedPane.getTitleAt(index),
                )
                val text = dialog.getText()
                if (!text.isNullOrBlank()) {
                    tabbedPane.setTitleAt(index, text)
                }
            }

        }

        // 克隆
        val clone = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.clone"))
        clone.addActionListener {
            val index = tabbedPane.selectedIndex
            if (index > 0) {
                val tab = tabs[index]
                if (tab is HostTerminalTab) {
                    ActionManager.getInstance()
                        .getAction(Actions.OPEN_HOST)
                        .actionPerformed(OpenHostActionEvent(this, tab.host))
                }
            }
        }

        // 在新窗口中打开
        val openInNewWindow = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.open-in-new-window"))
        openInNewWindow.addActionListener {
            val index = tabbedPane.selectedIndex
            if (index > 0) {
                removeTabAt(index, false)
                val dialog = TerminalTabDialog(
                    owner = SwingUtilities.getWindowAncestor(this),
                    terminalTab = tab,
                    size = Dimension(min(size.width, 1280), min(size.height, 800))
                )
                Disposer.register(dialog, tab)
                Disposer.register(this, dialog)
                dialog.isVisible = true
            }
        }

        popupMenu.addSeparator()

        // 关闭
        val close = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.close"))
        close.addActionListener {
            tabbedPane.tabCloseCallback?.accept(tabbedPane, tabIndex)
        }

        // 关闭其他标签页
        popupMenu.add(I18n.getString("termora.tabbed.contextmenu.close-other-tabs")).addActionListener {
            for (i in tabbedPane.tabCount - 1 downTo tabIndex + 1) {
                tabbedPane.tabCloseCallback?.accept(tabbedPane, i)
            }
            for (i in 1 until tabIndex) {
                tabbedPane.tabCloseCallback?.accept(tabbedPane, tabIndex - i)
            }
        }

        // 关闭所有标签页
        popupMenu.add(I18n.getString("termora.tabbed.contextmenu.close-all-tabs")).addActionListener {
            for (i in 0 until tabbedPane.tabCount) {
                tabbedPane.tabCloseCallback?.accept(tabbedPane, tabbedPane.tabCount - 1)
            }
        }


        close.isEnabled = c !is WelcomePanel
        rename.isEnabled = close.isEnabled
        clone.isEnabled = close.isEnabled
        openInNewWindow.isEnabled = close.isEnabled

        // 如果不允许克隆
        if (clone.isEnabled && !tab.canClone()) {
            clone.isEnabled = false
        }

        if (close.isEnabled) {
            popupMenu.addSeparator()
            val reconnect = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.reconnect"))
            reconnect.addActionListener {
                val index = tabbedPane.selectedIndex
                if (index > 0) {
                    tabs[index].reconnect()
                }
            }

            reconnect.isEnabled = tabs[tabIndex].canReconnect()
        }

        popupMenu.show(this, e.x, e.y)
    }


    fun addTab(tab: TerminalTab) {
        tabbedPane.addTab(
            tab.getTitle(),
            tab.getIcon(),
            tab.getJComponent()
        )

        // 监听 icons 变化
        tab.addPropertyChangeListener(iconListener)

        tabs.add(tab)
        tabbedPane.selectedIndex = tabbedPane.tabCount - 1
        Disposer.register(this, tab)
    }

    private inner class SwitchFindEverywhereResult(
        private val title: String,
        private val icon: Icon?,
        private val c: Component
    ) : FindEverywhereResult {

        override fun actionPerformed(e: ActionEvent) {
            tabbedPane.selectedComponent = c
        }

        override fun getIcon(isSelected: Boolean): Icon {
            if (isSelected) {
                if (!FlatLaf.isLafDark()) {
                    if (icon is DynamicIcon) {
                        return icon.dark
                    }
                }
            }
            return icon ?: super.getIcon(isSelected)
        }

        override fun toString(): String {
            return title
        }
    }


    override fun dispose() {
    }

    override fun addTerminalTab(tab: TerminalTab) {
        addTab(tab)
    }

    override fun getSelectedTerminalTab(): TerminalTab? {
        val index = tabbedPane.selectedIndex
        if (index == -1) {
            return null
        }

        return tabs[index]
    }

    override fun getTerminalTabs(): List<TerminalTab> {
        return tabs
    }

    override fun setSelectedTerminalTab(tab: TerminalTab) {
        for (index in tabs.indices) {
            if (tabs[index] == tab) {
                tabbedPane.selectedIndex = index
                break
            }
        }
    }


}