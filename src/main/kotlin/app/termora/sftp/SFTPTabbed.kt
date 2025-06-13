package app.termora.sftp

import app.termora.*
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.database.DatabaseChangedExtension
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JButton
import javax.swing.JToolBar
import javax.swing.SwingUtilities

@Suppress("DuplicatedCode")
class SFTPTabbed(private val transportManager: TransportManager) : FlatTabbedPane(), Disposable {
    private val addBtn = JButton(Icons.add)
    private val tabbed = this
    private val disposed = AtomicBoolean(false)
    private val hostManager get() = HostManager.getInstance()

    val isDisposed get() = disposed.get()

    init {
        initViews()
        initEvents()
    }

    private fun initViews() {
        super.setTabLayoutPolicy(SCROLL_TAB_LAYOUT)
        super.setTabsClosable(true)
        super.setTabType(TabType.underlined)

        val toolbar = JToolBar()
        toolbar.add(addBtn)
        super.setTrailingComponent(toolbar)

    }

    private fun initEvents() {
        addBtn.addActionListener(object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                for (i in 0 until tabCount) {
                    val c = getComponentAt(i)
                    if (c !is SFTPFileSystemViewPanel) continue
                    if (c.state != SFTPFileSystemViewPanel.State.Initialized) continue
                    selectedIndex = i
                    return
                }

                // 添加一个新的
                addTab(
                    I18n.getString("termora.transport.sftp.select-host"),
                    SFTPFileSystemViewPanel(transportManager = transportManager)
                )
                selectedIndex = tabCount - 1
            }
        })

        // 右键菜单
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!SwingUtilities.isRightMouseButton(e)) {
                    return
                }

                val index = indexAtLocation(e.x, e.y)
                if (index < 0) return

                showContextMenu(index, e)
            }
        })

    }

    private fun showContextMenu(tabIndex: Int, e: MouseEvent) {
        val panel = getFileSystemViewPanel(tabIndex) ?: return
        val popupMenu = FlatPopupMenu()
        // 克隆
        val clone = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.clone"))
        clone.addActionListener(object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                val host = hostManager.getHost(panel.host.id) ?: return
                addSFTPFileSystemViewPanelTab(
                    host.copy(
                        options = host.options.copy(
                            sftpDefaultDirectory = panel.getWorkdir().absolutePathString()
                        )
                    )
                )
            }
        })

        // 编辑
        val edit = popupMenu.add(I18n.getString("termora.keymgr.edit"))
        edit.addActionListener(object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                if (panel.host.id == "local") {
                    return
                }
                val host = hostManager.getHost(panel.host.id) ?: return
                val dialog = NewHostDialogV2(evt.window, host)
                dialog.setLocationRelativeTo(evt.window)
                dialog.isVisible = true
                hostManager.addHost(dialog.host ?: return, DatabaseChangedExtension.Source.Sync)
            }
        })

        clone.isEnabled = panel.host.id != "local"
        edit.isEnabled = clone.isEnabled

        popupMenu.show(this, e.x, e.y)
    }

    fun addSFTPFileSystemViewPanelTab(host: Host) {
        val panel = SFTPFileSystemViewPanel(host, transportManager)
        addTab(host.name, panel)
        panel.connect()
        selectedIndex = tabCount - 1
    }

    /**
     * 获取当前的 FileSystemViewPanel
     */
    fun getSelectedFileSystemViewPanel(): FileSystemViewPanel? {
        return getFileSystemViewPanel(selectedIndex)
    }


    fun getFileSystemViewPanel(index: Int): FileSystemViewPanel? {
        if (tabCount < 1 || index < 0) return null

        val c = getComponentAt(index)
        if (c is FileSystemViewPanel) {
            return c
        }

        if (c is SFTPFileSystemViewPanel) {
            return c.getData(SFTPDataProviders.FileSystemViewPanel)
        }

        return null
    }

    override fun updateUI() {
        styleMap = mapOf(
            "focusColor" to DynamicColor("TabbedPane.background"),
            "hoverColor" to DynamicColor("TabbedPane.background"),
            "tabHeight" to 30
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
        if (disposed.compareAndSet(false, true)) {
            while (tabCount > 0) removeTabAt(0)
        }
    }
}