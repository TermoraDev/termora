package app.termora.transfer

import app.termora.*
import app.termora.account.AccountManager
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.database.DatabaseChangedExtension
import app.termora.database.DatabaseManager
import app.termora.plugin.internal.local.LocalProtocolProvider
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import org.apache.commons.lang3.SystemUtils
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.FileSystems
import javax.swing.JButton
import javax.swing.JOptionPane
import javax.swing.JToolBar
import javax.swing.SwingUtilities

@Suppress("DuplicatedCode")
internal class TransportTabbed(
    private val transferManager: TransferManager,
) : FlatTabbedPane(), Disposable {
    private val addBtn = JButton(Icons.add)
    private val tabbed get() = this
    lateinit var internalTransferManager: InternalTransferManager

    init {
        initViews()
        initEvents()
    }

    private fun initViews() {
        super.setTabLayoutPolicy(SCROLL_TAB_LAYOUT)
        super.setTabsClosable(true)
        super.setTabType(TabType.underlined)
        super.setFocusable(false)


        val toolbar = JToolBar()
        toolbar.add(addBtn)
        super.setTrailingComponent(toolbar)

    }

    private fun initEvents() {
        addBtn.addActionListener(object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                for (i in 0 until tabCount) {
                    val c = getComponentAt(i)
                    if (c !is TransportSelectionPanel) continue
                    if (c.state != TransportSelectionPanel.State.Initialized) continue
                    selectedIndex = i
                    SwingUtilities.invokeLater { c.requestFocusInWindow() }
                    return
                }

                // 添加一个新的
                addSelectionTab()
            }
        })

        // 右键菜单
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val index = indexAtLocation(e.x, e.y)
                if (index < 0) return
                if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenu(index, e)
                } else if (SwingUtilities.isLeftMouseButton(e) && e.clickCount % 2 == 0) {
                    val tab = getTransportPanel(index) ?: return
                    if (tab.loader.isOpened().not()) {
                        tab.reload()
                    }
                }
            }
        })


        // 关闭 tab
        setTabCloseCallback { _, i -> tabCloseCallback(i) }
    }

    fun tabCloseCallback(index: Int) {
        assertEventDispatchThread()

        if (isTabClosable(index).not()) return

        val c = tabbed.getComponentAt(index)
        if (c == null) {
            tabbed.removeTabAt(index)
            return
        }

        if (c is TransportPanel) {
            if (tabClose(c).not()) return
        }

        // 删除并销毁
        tabbed.removeTabAt(index)

        if (tabbed.tabCount < 1) {
            addSelectionTab()
        }
    }

    private fun tabClose(c: TransportPanel): Boolean {
        if (transferManager.getTransferCount() < 1) return true
        val loader = c.loader
        if (loader.isLoaded().not()) return false
        val fileSystem = loader.getSyncTransportSupport()
        val transfers = transferManager.getTransfers()
            .filter { it.source().fileSystem == fileSystem || it.target().fileSystem == fileSystem }
        if (transfers.isEmpty()) return true

        if (OptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(this),
                I18n.getString("termora.transport.sftp.close-tab"),
                messageType = JOptionPane.QUESTION_MESSAGE,
                optionType = JOptionPane.OK_CANCEL_OPTION
            ) != JOptionPane.OK_OPTION
        ) return false

        // 删除所有关联任务
        for (transfer in transfers) {
            transferManager.removeTransfer(transfer.id())
        }

        return true
    }

    fun addSelectionTab(): TransportSelectionPanel {
        val c = TransportSelectionPanel(tabbed, internalTransferManager)
        addTab(I18n.getString("termora.transport.sftp.select-host"), c)
        selectedIndex = tabCount - 1
        SwingUtilities.invokeLater { c.requestFocusInWindow() }
        return c
    }

    fun addLocalTab() {
        // local id 必须固定，不然书签无法使用
        val host = Host(id = "local", name = "Local", protocol = LocalProtocolProvider.PROTOCOL)
        val fs = FileSystems.getDefault()
        val support = DefaultTransportSupport(fs, fs.getPath(getDefaultLocalPath()))
        val panel = TransportPanel(internalTransferManager, host, object : TransportSupportLoader {
            override suspend fun getTransportSupport(): TransportSupport {
                return support
            }

            override fun getSyncTransportSupport(): TransportSupport {
                return support
            }

            override fun isLoaded(): Boolean {
                return true
            }
        })
        addTab(I18n.getString("termora.transport.local"), panel)
        super.setTabClosable(0, false)
    }

    private fun getDefaultLocalPath(): String {
        val defaultDirectory = DatabaseManager.getInstance().sftp.defaultDirectory
        if (defaultDirectory.isBlank()) return SystemUtils.USER_HOME
        return defaultDirectory
    }

    private fun showContextMenu(tabIndex: Int, e: MouseEvent) {
        val panel = getTransportPanel(tabIndex) ?: return
        val popupMenu = FlatPopupMenu()

        // 克隆
        val clone = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.clone"))
        clone.addActionListener(object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                val c = addSelectionTab()
                c.connect(panel.host)
            }
        })

        // 编辑
        val edit = popupMenu.add(I18n.getString("termora.keymgr.edit"))
        edit.addActionListener(object : AnAction() {
            private val hostManager get() = HostManager.getInstance()
            private val accountManager get() = AccountManager.getInstance()
            override fun actionPerformed(evt: AnActionEvent) {
                val window = evt.window
                val dialog = NewHostDialogV2(
                    window,
                    getHost(panel),
                    accountOwner = accountManager.getOwners().first { it.id == panel.host.ownerId })
                dialog.setLocationRelativeTo(window)
                dialog.title = panel.host.name
                dialog.isVisible = true
                val host = dialog.host ?: return
                hostManager.addHost(host, DatabaseChangedExtension.Source.User)
                setTitleAt(tabIndex, host.name)
                setHost(panel, host)
            }


            private fun getHost(panel: TransportPanel): Host {
                return panel.getClientProperty("EditHost") as Host? ?: panel.host
            }

            private fun setHost(panel: TransportPanel, host: Host) {
                panel.putClientProperty("EditHost", host)
            }
        })

        edit.isEnabled = clone.isEnabled

        popupMenu.show(this, e.x, e.y)
    }

    fun getSelectedTransportPanel(): TransportPanel? {
        val index = selectedIndex
        if (index < 0) return null
        return getTransportPanel(index)
    }

    fun getTransportPanel(index: Int): TransportPanel? {
        return getComponentAt(index) as? TransportPanel
    }

    override fun updateUI() {
        styleMap = mapOf(
            "focusColor" to DynamicColor("TabbedPane.background"),
            "hoverColor" to DynamicColor("TabbedPane.background"),
            "tabHeight" to 30,
            "showTabSeparators" to true,
            "tabSeparatorsFullHeight" to true,
        )
        super.updateUI()
    }

    override fun removeTabAt(index: Int) {
        val c = getComponentAt(index)
        if (c is Disposable) {
            Disposer.dispose(c)
        }
        super.removeTabAt(index)
    }

    override fun dispose() {
        while (tabCount > 0) removeTabAt(0)
    }
}