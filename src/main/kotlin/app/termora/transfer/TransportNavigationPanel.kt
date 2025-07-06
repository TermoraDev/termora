package app.termora.transfer

import app.termora.DynamicColor
import app.termora.Icons
import app.termora.transfer.TransportPanel.Companion.isWindowsFileSystem
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatTextField
import com.formdev.flatlaf.extras.components.FlatToolBar
import com.formdev.flatlaf.ui.FlatLineBorder
import com.formdev.flatlaf.util.SystemInfo
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Insets
import java.awt.Point
import java.awt.event.*
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.nio.file.Path
import javax.swing.*
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import kotlin.io.path.absolutePathString
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.math.round

internal class TransportNavigationPanel(private val navigator: TransportNavigator) : JPanel() {

    companion object {
        private const val TEXT_FIELD = "TextField"
        private const val SEGMENTS = "Segments"

        private val ICON_SIZE = if (SystemInfo.isMacOS) 14 else 16
        private val icon = FlatSVGIcon(Icons.playForward.name, ICON_SIZE, ICON_SIZE)
        private val moreHorizontal = FlatSVGIcon(Icons.moreHorizontal.name, ICON_SIZE, ICON_SIZE)
        private val computerIcon = FlatSVGIcon(Icons.desktop.name, ICON_SIZE, ICON_SIZE)

    }

    private val layeredPane = LayeredPane()
    private val textField = FlatTextField()
    private val downBtn = JButton(Icons.chevronDown)
    private val comboBox = object : JComboBox<Path>() {
        override fun getLocationOnScreen(): Point {
            val point = super.getLocationOnScreen()
            point.y -= 1
            return point
        }
    }
    private val segmentPanel = object : FlatToolBar() {
        override fun updateUI() {
            super.updateUI()
            border = FlatLineBorder(
                Insets(1, 0, 1, 0), DynamicColor.BorderColor,
                1f, UIManager.getInt("TextComponent.arc")
            )
        }
    }
    private val cardLayout = CardLayout()
    private val that get() = this

    init {
        initView()
        initEvents()
    }

    private fun initView() {
        super.setLayout(cardLayout)
        comboBox.isEnabled = false
        comboBox.putClientProperty("JComboBox.isTableCellEditor", true)
        comboBox.border = BorderFactory.createEmptyBorder()

        textField.trailingComponent = downBtn
        downBtn.putClientProperty(
            FlatClientProperties.STYLE,
            mapOf(
                "toolbar.hoverBackground" to UIManager.getColor("Button.background"),
                "toolbar.pressedBackground" to UIManager.getColor("Button.background"),
            )
        )

        segmentPanel.layout = BoxLayout(segmentPanel, BoxLayout.X_AXIS)
        segmentPanel.putClientProperty(
            FlatClientProperties.STYLE,
            mapOf("background" to DynamicColor("TextField.background"))
        )
        segmentPanel.isFocusable = true

        layeredPane.add(comboBox, JLayeredPane.DEFAULT_LAYER as Any)
        layeredPane.add(textField, JLayeredPane.PALETTE_LAYER as Any)

        add(layeredPane, TEXT_FIELD)
        add(segmentPanel, SEGMENTS)

        cardLayout.show(this, SEGMENTS)

    }

    private fun initEvents() {

        val itemListener = object : ItemListener {
            override fun itemStateChanged(e: ItemEvent) {
                val path = comboBox.selectedItem as Path? ?: return
                navigator.navigateTo(path.absolutePathString())
            }
        }

        segmentPanel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                cardLayout.show(that, TEXT_FIELD)
                textField.requestFocusInWindow()
            }
        })

        segmentPanel.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                val workdir = navigator.workdir ?: return
                repack(workdir)
            }
        })

        textField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                if (comboBox.isPopupVisible) return
                cardLayout.show(that, SEGMENTS)
            }
        })


        downBtn.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (comboBox.isPopupVisible) return
                comboBox.isEnabled = true
                comboBox.removeAllItems()
                for (path in navigator.getHistory()) {
                    comboBox.addItem(path)
                }
                comboBox.selectedItem = navigator.workdir
                comboBox.requestFocusInWindow()
                comboBox.showPopup()
            }
        })

        comboBox.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
                comboBox.addItemListener(itemListener)
            }

            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
                textField.requestFocusInWindow()
                comboBox.isEnabled = false
                comboBox.removeItemListener(itemListener)
            }

            override fun popupMenuCanceled(e: PopupMenuEvent?) {
                textField.requestFocusInWindow()
                comboBox.isEnabled = false
                comboBox.removeItemListener(itemListener)
            }

        })

        textField.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (navigator.loading) return
                if (textField.text.isBlank()) return
                navigator.navigateTo(textField.text)
            }
        })

        navigator.addPropertyChangeListener("workdir", object : PropertyChangeListener {
            override fun propertyChange(evt: PropertyChangeEvent) {
                val path = evt.newValue as? Path ?: return
                setTextFieldText(path)
                repack(path)
            }
        })
    }

    private fun setTextFieldText(path: Path) {
        if (path.fileSystem.isWindowsFileSystem() && path.pathString == path.fileSystem.separator) {
            textField.text = StringUtils.EMPTY
        } else {
            textField.text = FilenameUtils.separatorsToUnix(path.absolutePathString())
        }
    }

    private fun repack(workdir: Path) {
        segmentPanel.removeAll()

        var parent: Path? = workdir
        val fileSystem = workdir.fileSystem
        val parents = mutableListOf<Path>()

        while (parent != null) {
            parents.addFirst(parent)
            parent = parent.parent
            // Windows 比较特殊，因为它有盘符
            if (parent == null && fileSystem.isWindowsFileSystem()) {
                parents.addFirst(fileSystem.getPath(fileSystem.separator))
            }
        }

        // 预留点击空间
        val width = segmentPanel.width - 100
        val children = mutableListOf<JComponent>()

        for (i in 0 until parents.size) {
            val path = parents[i]
            val button = if (i == 0) JLabel(computerIcon)
            else if (fileSystem.isWindowsFileSystem() && path.root == path)
                JButton(path.toString().replace(fileSystem.separator, StringUtils.EMPTY))
            else JButton(path.name)
            // JLabel 与 JButton 对齐
            if (SystemUtils.IS_OS_MAC_OSX) {
                if (button is JLabel)
                    button.border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
                else
                    button.putClientProperty(FlatClientProperties.STYLE, mapOf("margin" to Insets(1, 2, 1, 2)))
            } else if (SystemUtils.IS_OS_LINUX) {
                if (button is JLabel)
                    button.border = BorderFactory.createEmptyBorder(0, 4, 0, 4)
                else
                    button.putClientProperty(FlatClientProperties.STYLE, mapOf("margin" to Insets(0, 0, 0, 0)))
            } else {
                if (button is JLabel)
                    button.border = BorderFactory.createEmptyBorder(3, 4, 3, 4)
                else
                    button.putClientProperty(FlatClientProperties.STYLE, mapOf("margin" to Insets(1, 2, 1, 2)))
            }
            button.isFocusable = false
            button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
            button.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        if (navigator.loading) return
                        if (path == navigator.workdir) {
                            setTextFieldText(path)
                        } else {
                            if (path.fileSystem.isWindowsFileSystem() && path.pathString == path.fileSystem.separator) {
                                navigator.navigateTo(path.pathString)
                            } else {
                                navigator.navigateTo(path.absolutePathString())
                            }
                        }
                    }
                }
            })
            button.putClientProperty("Path", path)
            children.add(button)

        }

        if (children.isEmpty()) {
            revalidate()
            repaint()
            return
        }

        val moreChildren = mutableListOf<Path>()
        val rightBtnWidth = createRightLabel().preferredSize.width
        var childrenWidth = children.first().preferredSize.width - rightBtnWidth

        var i = 1
        while (i < children.size) {
            val child = children[i]
            if (child.preferredSize.width + childrenWidth <= width) {
                childrenWidth += (child.preferredSize.width + rightBtnWidth)
            } else {
                i--
                if (children.size < 2 || i < 0) break
                val c = children.removeAt(1)
                val path = c.getClientProperty("Path") as Path
                moreChildren.add(path)
                childrenWidth -= (c.preferredSize.width + rightBtnWidth)
                continue
            }
            i++
        }

        for (n in 0 until children.size) {
            val child = children[n]
            segmentPanel.add(child)
            if (n != children.size - 1 || (moreChildren.isNotEmpty() && n == 0)) {
                segmentPanel.add(createRightLabel())
            }

            if (moreChildren.isNotEmpty()) {
                val button = JButton(moreHorizontal)
                // JLabel 与 JButton 对齐
                button.putClientProperty(FlatClientProperties.STYLE, mapOf("margin" to Insets(1, 2, 1, 2)))
                button.isFocusable = false
                button.putClientProperty(
                    FlatClientProperties.BUTTON_TYPE,
                    FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON
                )
                val paths = moreChildren.toTypedArray()
                button.addActionListener { showMoreContextmenu(button, paths) }
                segmentPanel.add(button)

                if (n != children.size - 1) {
                    segmentPanel.add(createRightLabel())
                }

                moreChildren.clear()
            }
        }

        segmentPanel.add(Box.createHorizontalGlue())
        val downBtn = JLabel(Icons.chevronDown)
        downBtn.border = BorderFactory.createEmptyBorder(2, 2, 2, 3)
        downBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    cardLayout.show(that, TEXT_FIELD)
                    SwingUtilities.invokeLater { that.downBtn.doClick() }
                }
            }
        })

        segmentPanel.add(downBtn)

        revalidate()
        repaint()

    }

    private fun showMoreContextmenu(button: JButton, paths: Array<Path>) {
        val popupMenu = FlatPopupMenu()
        for (item in paths) {
            var text = item.name
            if (item.fileSystem.isWindowsFileSystem()) {
                if (item.root == item) {
                    text = item.pathString
                }
            }
            popupMenu.add(text).addActionListener { navigator.navigateTo(item.absolutePathString()) }
        }
        popupMenu.show(
            button,
            button.x - button.width / 2 - popupMenu.preferredSize.width / 2,
            button.y + button.height + 1
        )
    }

    private fun createRightLabel(): JLabel {
        val rightBtn = JLabel(icon)
        rightBtn.preferredSize = Dimension(
            round(rightBtn.preferredSize.width / 1.5).toInt(),
            rightBtn.preferredSize.height
        )
        rightBtn.maximumSize = rightBtn.preferredSize
        rightBtn.isFocusable = false
        rightBtn.putClientProperty(
            FlatClientProperties.BUTTON_TYPE,
            FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON
        )
        rightBtn.addMouseListener(object : MouseAdapter() {})
        return rightBtn
    }

    private class LayeredPane : JLayeredPane() {
        override fun doLayout() {
            synchronized(treeLock) {
                for (c in components) {
                    c.setBounds(0, 0, width, height)
                }
            }
        }
    }

}