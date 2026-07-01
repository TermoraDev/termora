package app.termora.plugin.internal.cli

import app.termora.Host
import app.termora.HostManager
import app.termora.I18n
import app.termora.Icons
import app.termora.OptionsPane
import app.termora.cli.guard.WhitelistManager
import app.termora.database.DatabaseManager
import app.termora.protocol.ProtocolProvider
import com.formdev.flatlaf.icons.FlatTreeClosedIcon
import com.formdev.flatlaf.icons.FlatTreeOpenIcon
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.border.EmptyBorder
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer

/**
 * 设置页「命令行工具」：树形分组展示主机，勾选哪些允许被 AI（termora-cli）访问，实时写入 cli.whitelist。
 */
class CliWhitelistOption : JPanel(BorderLayout()), OptionsPane.Option {

    private val store = WhitelistStore(
        read = { DatabaseManager.getInstance().properties.getString(WhitelistStore.SETTING_KEY, "[]") },
        write = { DatabaseManager.getInstance().properties.putString(WhitelistStore.SETTING_KEY, it) },
    )

    private val properties get() = DatabaseManager.getInstance().properties

    /** termora-cli 总开关复选框；始终可点（不随自身禁用），切换时联动启停 [controls]。 */
    private val enableCheckBox = JCheckBox(I18n.getString("termora.settings.cli.enable"))

    /** 需随总开关启停的控件（白名单树、滚动面板、skill/path 按钮、tip 等）；开关本身不在其中。 */
    private val controls = mutableListOf<JComponent>()

    /** 白名单快照（host id 集合）；渲染器据此判断勾选状态，避免每行重绘都解析一次 JSON。 */
    private var enabledSnapshot: Set<String> = emptySet()

    init {
        initView()
    }

    private fun initView() {
        // termora-cli 总开关（north 区最顶部，tip 之上）；默认关闭，切换时写 cli.enabled 并联动启停其它控件
        enableCheckBox.isSelected =
            properties.getString(WhitelistManager.ENABLED_KEY, "false").toBooleanStrictOrNull() ?: false
        enableCheckBox.toolTipText = I18n.getString("termora.settings.cli.enable.tip")
        enableCheckBox.border = EmptyBorder(8, 8, 0, 8)
        enableCheckBox.addActionListener {
            properties.putString(WhitelistManager.ENABLED_KEY, enableCheckBox.isSelected.toString())
            setControlsEnabled(enableCheckBox.isSelected)
        }

        val tip = JLabel(I18n.getString("termora.settings.cli.whitelist.tip"))
        tip.border = EmptyBorder(8, 8, 4, 8)
        controls.add(tip)

        // skill 按钮行
        val skillToolbar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        skillToolbar.border = EmptyBorder(0, 6, 0, 8)
        val copyButton = JButton(I18n.getString("termora.settings.cli.skill.copy"))
        val claudeButton = JButton(I18n.getString("termora.settings.cli.skill.install.claude"))
        val codexButton = JButton(I18n.getString("termora.settings.cli.skill.install.codex"))
        copyButton.addActionListener { copySkillToClipboard() }
        claudeButton.addActionListener { installSkill(SkillInstaller.claudeBaseDir()) }
        codexButton.addActionListener { installSkill(SkillInstaller.codexBaseDir()) }
        skillToolbar.add(copyButton)
        skillToolbar.add(claudeButton)
        skillToolbar.add(codexButton)
        controls.add(copyButton)
        controls.add(claudeButton)
        controls.add(codexButton)

        // PATH 按钮行（按钮变多，分两行布局确保都可见）
        val pathToolbar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        pathToolbar.border = EmptyBorder(0, 6, 4, 8)
        val addPathButton = JButton(I18n.getString("termora.settings.cli.path.add"))
        val removePathButton = JButton(I18n.getString("termora.settings.cli.path.remove"))
        addPathButton.addActionListener { addToPath() }
        removePathButton.addActionListener { removeFromPath() }
        pathToolbar.add(addPathButton)
        pathToolbar.add(removePathButton)
        controls.add(addPathButton)
        controls.add(removePathButton)

        // 两行按钮栏（skill + path）纵向排列
        val toolbar = JPanel()
        toolbar.layout = BoxLayout(toolbar, BoxLayout.Y_AXIS)
        skillToolbar.alignmentX = LEFT_ALIGNMENT
        pathToolbar.alignmentX = LEFT_ALIGNMENT
        toolbar.add(skillToolbar)
        toolbar.add(pathToolbar)

        // north 区（开关 + tip + 按钮栏）在空主机早退判断之前就 add，确保无主机时开关与按钮依然可见
        val north = JPanel(BorderLayout())
        val northTop = JPanel(BorderLayout())
        northTop.add(enableCheckBox, BorderLayout.NORTH)   // 开关在最顶部
        northTop.add(tip, BorderLayout.SOUTH)
        north.add(northTop, BorderLayout.NORTH)
        north.add(toolbar, BorderLayout.SOUTH)
        add(north, BorderLayout.NORTH)

        val hosts = HostManager.getInstance().hosts()
        val hasHost = hosts.any { !it.isFolder && !it.deleted }
        if (!hasHost) {
            val empty = JLabel(I18n.getString("termora.settings.cli.whitelist.empty"))
            empty.border = EmptyBorder(8, 8, 8, 8)
            add(empty, BorderLayout.CENTER)
            controls.add(empty)
            setControlsEnabled(enableCheckBox.isSelected)
            return
        }

        val root = HostTreeBuilder.build(hosts)
        val tree = JTree(root)
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = WhitelistTreeCellRenderer()
        tree.isOpaque = true
        // 初始化白名单快照供渲染器使用
        enabledSnapshot = store.ids()
        // 默认展开全部
        var i = 0
        while (i < tree.rowCount) { tree.expandRow(i); i++ }

        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val path = tree.getPathForLocation(e.x, e.y) ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                val host = node.userObject as? Host ?: return
                if (host.isFolder) {
                    if (tree.isExpanded(path)) tree.collapsePath(path) else tree.expandPath(path)
                } else {
                    if (!host.protocol.equals("SSH", ignoreCase = true)) return   // 非 SSH 不可勾选
                    store.setEnabled(host.id, !store.isEnabled(host.id))
                    enabledSnapshot = store.ids()   // 写入后刷新快照，供渲染器读取
                    tree.repaint()
                }
            }
        })

        val scrollPane = JScrollPane(tree)
        add(scrollPane, BorderLayout.CENTER)
        // 收集联动控件：JTree 在 JScrollPane 内时，须对 tree 本身禁用才能阻止其选择/点击交互
        controls.add(scrollPane)
        controls.add(tree)

        // 末尾按当前开关状态初始化控件可用性
        setControlsEnabled(enableCheckBox.isSelected)
    }

    /** 根据总开关状态启停所有联动控件（开关复选框本身不受影响）。 */
    private fun setControlsEnabled(enabled: Boolean) {
        for (c in controls) c.isEnabled = enabled
    }

    override fun getIcon(isSelected: Boolean): Icon = Icons.terminal
    override fun getTitle(): String = I18n.getString("termora.settings.cli")
    override fun getJComponent(): JComponent = this

    private fun copySkillToClipboard() {
        try {
            val selection = StringSelection(SkillInstaller.readSkill())
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
            JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                I18n.getString("termora.settings.cli.skill.copied"),
            )
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                I18n.getString("termora.settings.cli.skill.failed", e.message ?: e.javaClass.simpleName),
                null, JOptionPane.ERROR_MESSAGE,
            )
        }
    }

    private fun installSkill(baseDir: File) {
        try {
            val file = SkillInstaller.installTo(baseDir)
            JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                I18n.getString("termora.settings.cli.skill.installed", file.absolutePath),
            )
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                I18n.getString("termora.settings.cli.skill.failed", e.message ?: e.javaClass.simpleName),
                null, JOptionPane.ERROR_MESSAGE,
            )
        }
    }

    private fun addToPath() {
        try {
            val location = PathInstaller.install()
            val msg = buildString {
                append(I18n.getString("termora.settings.cli.path.added", location))
                if (PathInstaller.isPortable()) {
                    append("\n\n").append(I18n.getString("termora.settings.cli.path.portable-note"))
                }
                if (PathInstaller.isDev()) {
                    append("\n\n").append(I18n.getString("termora.settings.cli.path.dev-note"))
                }
            }
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this), msg)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                I18n.getString("termora.settings.cli.path.failed", e.message ?: e.javaClass.simpleName),
                null, JOptionPane.ERROR_MESSAGE,
            )
        }
    }

    private fun removeFromPath() {
        try {
            PathInstaller.uninstall()
            JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                I18n.getString("termora.settings.cli.path.removed"),
            )
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                I18n.getString("termora.settings.cli.path.failed", e.message ?: e.javaClass.simpleName),
                null, JOptionPane.ERROR_MESSAGE,
            )
        }
    }

    private inner class WhitelistTreeCellRenderer : TreeCellRenderer {
        private val panel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0))
        private val checkBox = JCheckBox()
        private val label = JLabel()

        init {
            panel.isOpaque = true
            checkBox.isOpaque = false
            label.isOpaque = false
            panel.add(checkBox)
            panel.add(label)
        }

        override fun getTreeCellRendererComponent(
            tree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean,
        ): Component {
            val node = value as? DefaultMutableTreeNode
            val host = node?.userObject as? Host

            if (selected) {
                panel.background = UIManager.getColor("Tree.selectionBackground")
                label.foreground = UIManager.getColor("Tree.selectionForeground")
            } else {
                panel.background = UIManager.getColor("Tree.background")
                label.foreground = UIManager.getColor("Tree.foreground")
            }

            if (host == null || host.isFolder) {
                checkBox.isVisible = false
                label.icon = if (expanded) FlatTreeOpenIcon() else FlatTreeClosedIcon()
                label.text = host?.name ?: ""
            } else {
                val isSsh = host.protocol.equals("SSH", ignoreCase = true)
                checkBox.isVisible = true
                checkBox.isEnabled = isSsh
                checkBox.isSelected = isSsh && host.id in enabledSnapshot
                checkBox.background = panel.background
                label.isEnabled = isSsh
                label.icon = hostIcon(host)
                label.text = buildString {
                    append(host.name)
                    if (host.host.isNotBlank()) append("  ${host.username}@${host.host}:${host.port}")
                    if (!isSsh) append("  (${I18n.getString("termora.settings.cli.whitelist.ssh-only")})")
                }
            }
            return panel
        }

        private fun hostIcon(host: Host): Icon =
            runCatching { ProtocolProvider.valueOf(host.protocol)?.getIcon() }.getOrNull() ?: Icons.terminal
    }
}
