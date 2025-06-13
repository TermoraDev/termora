package app.termora


import app.termora.actions.*
import app.termora.database.DatabaseChangedExtension
import app.termora.database.DatabaseManager
import app.termora.findeverywhere.BasicFilterFindEverywhereProvider
import app.termora.findeverywhere.FindEverywhereProvider
import app.termora.findeverywhere.FindEverywhereResult
import app.termora.plugin.internal.sftppty.SFTPPtyProtocolProvider
import app.termora.plugin.internal.sftppty.SFTPPtyTerminalTab
import app.termora.plugin.internal.ssh.SSHProtocolProvider
import app.termora.terminal.DataKey
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import org.apache.commons.lang3.StringUtils
import java.awt.*
import java.awt.event.AWTEventListener
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import java.util.*
import javax.swing.*
import javax.swing.JTabbedPane.SCROLL_TAB_LAYOUT
import kotlin.math.min

class TerminalTabbed(
    private val windowScope: WindowScope,
    private val termoraToolBar: TermoraToolBar,
    private val tabbedPane: FlatTabbedPane,
) : JPanel(BorderLayout()), Disposable, TerminalTabbedManager, DataProvider {
    private val tabs = mutableListOf<TerminalTab>()
    private val customizeToolBarAWTEventListener = CustomizeToolBarAWTEventListener()
    private val toolbar = termoraToolBar.getJToolBar()
    private val actionManager = ActionManager.getInstance()
    private val dataProviderSupport = DataProviderSupport()
    private val appearance get() = DatabaseManager.getInstance().appearance
    private val titleProperty = randomUUID()
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

        tabbedPane.trailingComponent = toolbar

        add(tabbedPane, BorderLayout.CENTER)

        windowScope.getOrCreate(TerminalTabbedManager::class) { this }

        dataProviderSupport.addData(DataProviders.TerminalTabbed, this)
        dataProviderSupport.addData(DataProviders.TerminalTabbedManager, this)
    }


    private fun initEvents() {
        // 关闭 tab
        tabbedPane.setTabCloseCallback { _, i -> removeTabAt(i, true) }

        // 选中变动
        tabbedPane.addPropertyChangeListener("selectedIndex") { evt ->
            val oldIndex = evt.oldValue as Int
            val newIndex = evt.newValue as Int

            if (oldIndex >= 0 && tabs.size > newIndex) {
                tabs[oldIndex].onLostFocus()
            }

            tabbedPane.getComponentAt(newIndex).requestFocusInWindow()

            if (newIndex >= 0 && tabs.size > newIndex) {
                tabs[newIndex].onGrabFocus()
            }

        }


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
        FindEverywhereProvider.getFindEverywhereProviders(windowScope)
            .add(BasicFilterFindEverywhereProvider(object : FindEverywhereProvider {
                override fun find(pattern: String): List<FindEverywhereResult> {
                    val results = mutableListOf<FindEverywhereResult>()
                    for (i in 0 until tabbedPane.tabCount) {
                        val c = tabbedPane.getComponentAt(i)
                        if (c is JComponent && c.getClientProperty(FindEverywhereProvider.SKIP_FIND_EVERYWHERE) != null) {
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


        // 监听全局事件
        toolkit.addAWTEventListener(customizeToolBarAWTEventListener, AWTEvent.MOUSE_EVENT_MASK)

    }

    private fun removeTabAt(index: Int, disposable: Boolean = true) {
        if (tabbedPane.isTabClosable(index)) {
            val tab = tabs[index]

            // 询问是否可以关闭
            if (disposable) {
                // 如果开启了关闭确认，那么直接询问用户
                if (appearance.confirmTabClose) {
                    if (OptionPane.showConfirmDialog(
                            windowScope.window,
                            I18n.getString("termora.tabbed.tab.close-prompt"),
                            messageType = JOptionPane.QUESTION_MESSAGE,
                            optionType = JOptionPane.OK_CANCEL_OPTION
                        ) != JOptionPane.OK_OPTION
                    ) {
                        return
                    }
                } else if (!tab.willBeClose()) { // 如果没有开启则询问用户
                    return
                }
            }

            // 通知即将关闭
            if (disposable) {
                try {
                    tab.beforeClose()
                } catch (_: Exception) {
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

            // 新的真正获取焦点
            tabbedPane.getComponentAt(tabbedPane.selectedIndex).requestFocusInWindow()

            if (disposable) {
                Disposer.dispose(tab)
            }
        }
    }

    private fun showContextMenu(tabIndex: Int, e: MouseEvent) {
        val c = tabbedPane.getComponentAt(tabIndex) as JComponent
        val tab = tabs[tabIndex]

        val popupMenu = FlatPopupMenu()

        // 修改名称
        val rename = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.rename"))
        rename.addActionListener {
            if (tabIndex > 0) {
                val text = OptionPane.showInputDialog(
                    SwingUtilities.getWindowAncestor(this),
                    title = rename.text,
                    value = tabbedPane.getTitleAt(tabIndex)
                )
                if (!text.isNullOrBlank()) {
                    tabbedPane.setTitleAt(tabIndex, text)
                    c.putClientProperty(titleProperty, text)
                }
            }

        }

        // 克隆
        val clone = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.clone"))
        clone.addActionListener { evt ->
            if (tab is HostTerminalTab) {
                actionManager
                    .getAction(OpenHostAction.OPEN_HOST)
                    .actionPerformed(OpenHostActionEvent(this, tab.host, evt))
            }
        }

        // 编辑
        val edit = popupMenu.add(I18n.getString("termora.keymgr.edit"))
        edit.addActionListener(object : AnAction() {
            private val hostManager get() = HostManager.getInstance()
            override fun actionPerformed(evt: AnActionEvent) {
                if (tab is HostTerminalTab) {
                    val host = hostManager.getHost(tab.host.id) ?: return
                    val dialog = NewHostDialogV2(evt.window, host)
                    dialog.setLocationRelativeTo(evt.window)
                    dialog.isVisible = true

                    // Sync 模式，触发 reload
                    hostManager.addHost(dialog.host ?: return, DatabaseChangedExtension.Source.Sync)
                }
            }
        })

        // 在新窗口中打开
        val openInNewWindow = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.open-in-new-window"))
        openInNewWindow.addActionListener(object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                val owner = evt.getData(DataProviders.TermoraFrame) ?: return
                if (tabIndex > 0) {
                    val title = tabbedPane.getTitleAt(tabIndex)
                    removeTabAt(tabIndex, false)
                    val dialog = TerminalTabDialog(
                        owner = owner,
                        terminalTab = tab,
                        size = Dimension(min(size.width, 1280), min(size.height, 800))
                    )
                    dialog.title = title
                    Disposer.register(dialog, tab)
                    Disposer.register(this@TerminalTabbed, dialog)
                    dialog.isVisible = true
                }
            }
        })

        if (tab is HostTerminalTab) {
            val openHostAction = actionManager.getAction(OpenHostAction.OPEN_HOST)
            if (openHostAction != null) {
                if (tab.host.protocol == SSHProtocolProvider.PROTOCOL || tab.host.protocol == SFTPPtyProtocolProvider.PROTOCOL) {
                    popupMenu.addSeparator()
                    val sftpCommand = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.sftp-command"))
                    sftpCommand.addActionListener { openSFTPPtyTab(tab, openHostAction, it) }
                }
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


        close.isEnabled = tab.canClose()
        rename.isEnabled = close.isEnabled
        clone.isEnabled = close.isEnabled
        edit.isEnabled = tab is HostTerminalTab && tab.host.id != "local"
        openInNewWindow.isEnabled = close.isEnabled

        // 如果不允许克隆
        if (clone.isEnabled && !tab.canClone()) {
            clone.isEnabled = false
        }

        if (close.isEnabled) {
            popupMenu.addSeparator()
            val reconnect = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.reconnect"))
            reconnect.addActionListener {
                if (tabIndex > 0) {
                    tabs[tabIndex].reconnect()
                }
            }

            reconnect.isEnabled = tabs[tabIndex].canReconnect()
        }

        popupMenu.show(this, e.x, e.y)
    }


    private fun addTab(index: Int, tab: TerminalTab, selected: Boolean) {
        val c = tab.getJComponent()
        val title = (c.getClientProperty(titleProperty) ?: tab.getTitle()).toString()

        tabbedPane.insertTab(
            title,
            tab.getIcon(),
            c,
            StringUtils.EMPTY,
            index
        )

        // 设置标题
        c.putClientProperty(titleProperty, title)
        // 监听 icons 变化
        tab.addPropertyChangeListener(iconListener)

        tabs.add(index, tab)

        if (selected) {
            tabbedPane.selectedIndex = index
        }

        tabbedPane.setTabClosable(index, tab.canClose())

        Disposer.register(this, tab)
    }

    override fun refreshTerminalTabs() {
        for (i in 0 until tabbedPane.tabCount) {
            tabbedPane.setTabClosable(i, tabs[i].canClose())
        }
    }


    private fun openSFTPPtyTab(tab: HostTerminalTab, openHostAction: Action, evt: EventObject) {
        if (!SFTPPtyTerminalTab.canSupports) {
            OptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                I18n.getString("termora.tabbed.contextmenu.sftp-not-install"),
                messageType = JOptionPane.ERROR_MESSAGE
            )
            return
        }

        var host = tab.host

        if (host.protocol == SSHProtocolProvider.PROTOCOL) {
            val envs = tab.host.options.envs().toMutableMap()
            val currentDir = tab.getData(DataProviders.Terminal)?.getTerminalModel()
                ?.getData(DataKey.CurrentDir, StringUtils.EMPTY) ?: StringUtils.EMPTY

            if (currentDir.isNotBlank()) {
                envs["CurrentDir"] = currentDir
            }

            host = host.copy(
                protocol = SFTPPtyProtocolProvider.PROTOCOL,
                options = host.options.copy(env = envs.toPropertiesString())
            )
        }

        openHostAction.actionPerformed(OpenHostActionEvent(this, host, evt))
    }

    /**
     * 对着 ToolBar 右键
     */
    private inner class CustomizeToolBarAWTEventListener : AWTEventListener, Disposable {
        init {
            Disposer.register(this@TerminalTabbed, this)
        }

        override fun eventDispatched(event: AWTEvent) {
            if (event !is MouseEvent || event.id != MouseEvent.MOUSE_CLICKED || !SwingUtilities.isRightMouseButton(event)) return
            // 如果 ToolBar 没有显示
            if (!toolbar.isShowing) return
            // 如果不是作用于在 ToolBar 上面
            if (!Rectangle(toolbar.locationOnScreen, toolbar.size).contains(event.locationOnScreen)) return

            // 显示右键菜单
            showContextMenu(event)
        }

        private fun showContextMenu(event: MouseEvent) {
            val popupMenu = FlatPopupMenu()
            popupMenu.add(I18n.getString("termora.toolbar.customize-toolbar")).addActionListener {
                val owner = SwingUtilities.getWindowAncestor(this@TerminalTabbed)
                val dialog = CustomizeToolBarDialog(owner, windowScope, termoraToolBar)
                dialog.setLocationRelativeTo(owner)
                if (dialog.open()) {
                    TermoraToolBar.rebuild()
                }
            }
            popupMenu.show(event.component, event.x, event.y)
        }

        override fun dispose() {
            toolkit.removeAWTEventListener(this)
        }
    }

    /*private inner class CustomizeToolBarDialog(owner: Window) : DialogWrapper(owner) {
        init {
            size = Dimension(UIManager.getInt("Dialog.width"), UIManager.getInt("Dialog.height"))
            isModal = true
            title = I18n.getString("termora.setting")
            setLocationRelativeTo(null)

            init()
        }

        override fun createCenterPanel(): JComponent {
            val model = DefaultListModel<String>()
            val checkBoxList = CheckBoxList(model)
            checkBoxList.fixedCellHeight = UIManager.getInt("Tree.rowHeight")
            model.addElement("Test")
            return checkBoxList
        }

    }*/

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

    override fun addTerminalTab(tab: TerminalTab, selected: Boolean) {
        addTab(tabs.size, tab, selected)
    }

    override fun addTerminalTab(index: Int, tab: TerminalTab, selected: Boolean) {
        addTab(index, tab, selected)
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

    override fun closeTerminalTab(tab: TerminalTab, disposable: Boolean) {
        for (i in 0 until tabs.size) {
            if (tabs[i] == tab) {
                removeTabAt(i, disposable)
                break
            }
        }
    }

    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        if (dataKey == DataProviders.TerminalTab) {
            dataProviderSupport.removeData(dataKey)
            if (tabbedPane.selectedIndex >= 0 && tabs.size > tabbedPane.selectedIndex) {
                dataProviderSupport.addData(dataKey, tabs[tabbedPane.selectedIndex])
            }
        }
        return dataProviderSupport.getData(dataKey)
    }


}