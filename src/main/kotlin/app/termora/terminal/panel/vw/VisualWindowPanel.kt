package app.termora.terminal.panel.vw

import app.termora.*
import app.termora.database.DatabaseManager
import com.formdev.flatlaf.extras.components.FlatToolBar
import com.formdev.flatlaf.util.SystemInfo
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.math.max
import kotlin.math.min


open class VisualWindowPanel(protected val id: String, protected val visualWindowManager: VisualWindowManager) :
    JPanel(BorderLayout()), VisualWindow {

    private val stickPx = 2
    protected val properties get() = DatabaseManager.getInstance().properties
    private val titleLabel = JLabel()
    private val toolbar = FlatToolBar()
    private val visualWindow get() = this
    private val resizer = VisualWindowResizer(visualWindow) { !isWindow && !isStick }
    private var isWindow = false
        set(value) {
            val oldValue = field
            field = value
            firePropertyChange("isWindow", oldValue, value)
        }
    private var dialog: VisualWindowDialog? = null
    private var oldBounds = Rectangle()
    private var toggleWindowBtn = JButton(Icons.openInNewWindow)
    private var isAlwaysTop
        get() = properties.getString("VisualWindow.${id}.dialog.isAlwaysTop", "false").toBoolean()
        set(value) = properties.putString("VisualWindow.${id}.dialog.isAlwaysTop", value.toString())

    private val alwaysTopBtn = JButton(Icons.moveUp)
    private val closeWindowListener = object : WindowAdapter() {
        override fun windowClosed(e: WindowEvent) {
            close()
        }
    }

    protected var isStickHover = false
        private set(value) {
            if (value == field) return
            field = value
            reassemble()
        }
    protected var isStick: Boolean = false
        private set(value) {
            if (field == value) return
            if (!value) isStickHover = false
            field = value
            reassemble()
        }
    protected val expand get() = isWindow || isStickHover || !isStick

    protected var title: String
        set(value) = titleLabel.setText(value)
        get() = titleLabel.text


    protected fun initVisualWindowPanel() {
        initViews()
        initEvents()
        initToolBar()
    }

    private fun initViews() {
        border = BorderFactory.createMatteBorder(1, 1, 1, 1, DynamicColor.BorderColor)

        val x = properties.getString("VisualWindow.${id}.location.x", "-1").toIntOrNull() ?: -1
        val y = properties.getString("VisualWindow.${id}.location.y", "-1").toIntOrNull() ?: -1
        val w = properties.getString("VisualWindow.${id}.location.width", "-1").toIntOrNull() ?: -1
        val h = properties.getString("VisualWindow.${id}.location.height", "-1").toIntOrNull() ?: -1

        if (x >= 0 && y >= 0) {
            setLocation(x, y)
        } else {
            setLocation(200, 200)
        }

        if (w > 0 && h > 0) setSize(w, h) else setSize(400, 200)

        oldBounds = bounds
        alwaysTopBtn.isSelected = isAlwaysTop
        alwaysTopBtn.isVisible = false
    }

    protected open fun toolbarButtons(): List<JButton> {
        return emptyList()
    }

    private fun initEvents() {
        val dragListener = DragListener()
        toolbar.addMouseListener(dragListener)
        toolbar.addMouseMotionListener(dragListener)

        val awtEventListener = object : AWTEventListener {
            override fun eventDispatched(event: AWTEvent) {
                if (event is MouseEvent) {
                    if (event.id == MouseEvent.MOUSE_PRESSED) {
                        val c = event.component ?: return
                        if (SwingUtilities.isDescendingFrom(c, visualWindow)) {
                            visualWindowManager.moveToFront(visualWindow)
                        }
                    } else if (event.id == MouseEvent.MOUSE_MOVED) {
                        if (isStick && !isWindow) {
                            val c = event.component ?: return
                            isStickHover = SwingUtilities.isDescendingFrom(c, visualWindow)
                        }
                    }
                }
            }
        }

        // 监听全局事件
        toolkit.addAWTEventListener(awtEventListener, MouseEvent.MOUSE_EVENT_MASK or MouseEvent.MOUSE_MOTION_EVENT_MASK)

        Disposer.register(this, object : Disposable {
            override fun dispose() {
                toolkit.removeAWTEventListener(awtEventListener)
            }
        })

        // 阻止事件穿透
        addMouseListener(object : MouseAdapter() {})

        toggleWindowBtn.addActionListener { toggleWindow() }

        addPropertyChangeListener("isWindow") {
            if (isWindow) {
                border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor)
                toggleWindowBtn.icon = Icons.openInToolWindow
            } else {
                border = BorderFactory.createMatteBorder(1, 1, 1, 1, DynamicColor.BorderColor)
                toggleWindowBtn.icon = Icons.openInNewWindow
            }
        }

        // 被添加到组件后
        addPropertyChangeListener("ancestor", object : PropertyChangeListener {
            override fun propertyChange(evt: PropertyChangeEvent) {
                removePropertyChangeListener("ancestor", this)
                // 获取缓存是否是粘附
                val isStick = properties.getString("VisualWindow.${id}.stick", "false").toBoolean()
                if (isStick && bounds.y <= stickPx) {
                    visualWindow.isStick = true
                }
            }
        })

        alwaysTopBtn.addActionListener {
            isAlwaysTop = !isAlwaysTop
            alwaysTopBtn.isSelected = isAlwaysTop

            if (isWindow()) {
                dialog?.isAlwaysOnTop = isAlwaysTop
            }
        }
    }

    private fun initToolBar() {
        val btns = toolbarButtons()
        val count = 2 + btns.size
        toolbar.add(alwaysTopBtn)
        toolbar.add(Box.createHorizontalStrut(count * 26))
        toolbar.add(JLabel(Icons.empty))
        toolbar.add(Box.createHorizontalGlue())
        toolbar.add(titleLabel)
        toolbar.add(Box.createHorizontalGlue())

        btns.forEach { toolbar.add(it) }

        toolbar.add(toggleWindowBtn)
        toolbar.add(JButton(Icons.close).apply { addActionListener { Disposer.dispose(visualWindow) } })
        toolbar.border = BorderFactory.createMatteBorder(0, 0, 1, 0, DynamicColor.BorderColor)
        add(toolbar, BorderLayout.NORTH)
    }

    override fun dispose() {

        val bounds = if (isWindow || isStick) oldBounds else bounds
        properties.putString("VisualWindow.${id}.location.x", bounds.x.toString())
        properties.putString("VisualWindow.${id}.location.y", bounds.y.toString())
        properties.putString("VisualWindow.${id}.location.width", bounds.width.toString())
        properties.putString("VisualWindow.${id}.location.height", bounds.height.toString())
        properties.putString("VisualWindow.${id}.stick", isStick.toString())

        resizer.uninstall()

        this.close()

    }

    final override fun getJComponent(): JComponent {
        return this
    }

    override fun isWindow(): Boolean {
        return isWindow
    }

    override fun getWindow(): Window? {
        return dialog
    }

    protected open fun getWindowTitle(): String {
        return id
    }

    override fun toggleWindow() {

        if (isWindow) {
            // 提前移除 dialog 的关闭事件
            dialog?.removeWindowListener(closeWindowListener)
        }

        isWindow = !isWindow
        dialog?.dispose()
        dialog = null

        alwaysTopBtn.isVisible = isWindow

        if (isWindow) {
            oldBounds = bounds
            // 变基
            visualWindowManager.rebaseVisualWindow(this)

            // 如果在预览状态，那么设置成 false
            if (isStickHover) {
                isStickHover = false
            } else {
                reassemble()
            }

            val dialog = VisualWindowDialog().apply { dialog = this }
            dialog.addWindowListener(closeWindowListener)
            dialog.isVisible = true

        } else {
            bounds = oldBounds
            visualWindowManager.removeVisualWindow(visualWindow)
            visualWindowManager.addVisualWindow(visualWindow)
            // 重组
            reassemble()
        }
    }

    private inner class DragListener : MouseAdapter() {
        private var startPoint: Point? = null

        override fun mousePressed(e: MouseEvent) {
            if (isWindow) {
                startPoint = null
                return
            }
            startPoint = SwingUtilities.convertPoint(visualWindow, e.getPoint(), visualWindow.parent)
        }

        override fun mouseDragged(e: MouseEvent) {
            val startPoint = this.startPoint ?: return
            val newPoint = SwingUtilities.convertPoint(visualWindow, e.getPoint(), visualWindow.parent)
            val dimension = visualWindowManager.getDimension()

            val x = min(
                visualWindow.x + (newPoint.x - startPoint.x),
                dimension.width - visualWindow.width
            )

            val y = min(
                visualWindow.y + (newPoint.y - startPoint.y),
                dimension.height - visualWindow.height
            )

            if (isStick) {
                if (y > stickPx) {
                    isStick = false
                    return
                }
            } else {
                oldBounds = visualWindow.bounds
            }

            // 如果太靠近边缘，那么动态显示/隐藏边框
            border = BorderFactory.createMatteBorder(if (y <= 0) 0 else 1, 1, 1, 1, DynamicColor.BorderColor)

            visualWindow.setBounds(max(x, 0), max(y, 0), visualWindow.width, visualWindow.height)



            this.startPoint = newPoint
        }

        override fun mouseReleased(e: MouseEvent) {
            visualWindowManager.moveToFront(visualWindow)
            isStick = visualWindow.bounds.y <= stickPx
        }
    }

    /**
     * 重新组装
     */
    protected open fun reassemble() {
        if (expand) {
            border = BorderFactory.createMatteBorder(if (isStickHover) 0 else 1, 1, 1, 1, DynamicColor.BorderColor)
            visualWindow.setBounds(bounds.x, bounds.y, oldBounds.width, oldBounds.height)
        } else {
            val bounds = visualWindow.bounds
            visualWindow.setBounds(bounds.x, bounds.y, bounds.width, max(toolbar.height, toolbar.preferredSize.height))
            border = BorderFactory.createMatteBorder(0, 1, 1, 1, DynamicColor.BorderColor)
        }

        // 重新渲染
        SwingUtilities.invokeLater { SwingUtilities.updateComponentTreeUI(this) }
    }


    protected open fun close() {
        if (isWindow()) {
            dialog?.dispose()
            dialog = null
        }
        visualWindowManager.removeVisualWindow(visualWindow)
    }

    private inner class VisualWindowDialog : DialogWrapper(null) {

        init {
            isModal = false
            controlsVisible = false
            isResizable = true
            title = getWindowTitle()
            isAlwaysOnTop = isAlwaysTop

            if (SystemInfo.isWindows || SystemInfo.isLinux) {
                val sizes = listOf(16, 20, 24, 28, 32, 48, 64)
                val loader = TermoraFrame::class.java.classLoader
                val images = sizes.mapNotNull { e ->
                    loader.getResourceAsStream("icons/termora_${e}x${e}.png")?.use { ImageIO.read(it) }
                }
                iconImages = images
            }

            initEvents()

            init()


            val x = properties.getString("VisualWindow.${id}.dialog.location.x", "-1").toIntOrNull() ?: -1
            val y = properties.getString("VisualWindow.${id}.dialog.location.y", "-1").toIntOrNull() ?: -1
            val w = properties.getString("VisualWindow.${id}.dialog.location.width", "-1").toIntOrNull() ?: -1
            val h = properties.getString("VisualWindow.${id}.dialog.location.height", "-1").toIntOrNull() ?: -1

            if (w > 0 && h > 0) setSize(w, h) else pack()

            if (x >= 0 && y >= 0) {
                setLocation(x, y)
            } else {
                setLocationRelativeTo(null)
            }


        }

        private fun initEvents() {
            addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent) {
                    properties.putString("VisualWindow.${id}.dialog.location.x", x.toString())
                    properties.putString("VisualWindow.${id}.dialog.location.y", y.toString())
                    properties.putString("VisualWindow.${id}.dialog.location.width", width.toString())
                    properties.putString("VisualWindow.${id}.dialog.location.height", height.toString())
                }
            })
        }


        override fun createCenterPanel(): JComponent {
            return getJComponent()
        }

        override fun createSouthPanel(): JComponent? {
            return null
        }
    }

    override fun getWindowName(): String {
        return id
    }
}