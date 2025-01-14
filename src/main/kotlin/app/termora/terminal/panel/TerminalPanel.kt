package app.termora.terminal.panel

import app.termora.terminal.*
import com.formdev.flatlaf.util.SystemInfo
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDropEvent
import java.awt.event.*
import java.awt.font.TextHitInfo
import java.awt.im.InputMethodRequests
import java.io.File
import java.text.AttributedCharacterIterator
import java.text.AttributedString
import java.text.BreakIterator
import java.text.CharacterIterator
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


class TerminalPanel(val terminal: Terminal, private val ptyConnector: PtyConnector) :
    JPanel(BorderLayout()) {

    companion object {
        val Debug = DataKey(Boolean::class)
        val Finding = DataKey(Boolean::class)
        val SelectCopy = DataKey(Boolean::class)
    }

    private val floatingToolBar = TerminalFloatingToolBar(terminal)
    private val terminalFindPanel = TerminalFindPanel(this, terminal)
    private val terminalDisplay = TerminalDisplay(this, terminal)
    val scrollBar = TerminalScrollBar(this@TerminalPanel, terminalFindPanel, terminal)


    /**
     * 键盘事件
     */
    private val actions = mutableListOf(
        // 查找
        TerminalFindAction(this),
        // 全选
        TerminalSelectAllAction(terminal),
        // Zoom in
        TerminalZoomInAction(),
        // Zoom out
        TerminalZoomOutAction(),
        // Zoom reset
        TerminalZoomResetAction(),
        // 选择事件
        TerminalSelectionAction(terminal),
        // 复制
        TerminalCopyAction(this),
        // 粘贴
        TerminalPasteAction(this),
    )


    /**
     * 调试模式
     */
    var debug: Boolean
        get() = terminal.getTerminalModel().getData(Debug, false)
        set(value) = terminal.getTerminalModel().setData(Debug, value)

    /**
     * 是否显示查找标记
     */
    var findMap = true

    /**
     * 修改大小时是否提示
     */
    var resizeToast = true

    /**
     * 内边距
     */
    var padding = Insets(4, 4, 4, 4)
        set(value) {
            field = value
            repaintImmediate()
        }

    /**
     * Toast 总开关
     */
    var showToast = true

    /**
     * 是否支持文件拖拽
     */
    var dropFiles = false


    init {
        initView()
        initEvents()
    }


    private fun initView() {
        isFocusable = true
        isRequestFocusEnabled = true
        focusTraversalKeysEnabled = false


        enableEvents(AWTEvent.KEY_EVENT_MASK or AWTEvent.INPUT_METHOD_EVENT_MASK)
        enableInputMethods(true)

        scrollBar.minimum = 0
        scrollBar.maximum = 0
        scrollBar.value = 0
        scrollBar.unitIncrement = 1
        scrollBar.blockIncrement = 1
        background = Color.black


        val layeredPane = TerminalLayeredPane()
        layeredPane.add(terminalDisplay, JLayeredPane.DEFAULT_LAYER as Any)
        layeredPane.add(terminalFindPanel, JLayeredPane.POPUP_LAYER as Any)
        layeredPane.add(floatingToolBar.getJComponent(), JLayeredPane.POPUP_LAYER as Any)
        add(layeredPane, BorderLayout.CENTER)
        add(scrollBar, BorderLayout.EAST)

        hideFind()
    }

    private fun initEvents() {

        this.addKeyListener(TerminalPanelKeyAdapter(this, terminal, ptyConnector))

        this.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                repaintImmediate()
            }

            override fun focusGained(e: FocusEvent) {
                repaintImmediate()
            }
        })
        this.addComponentListener(TerminalPanelComponentAdapter(this, terminalDisplay, terminal, ptyConnector))

        // 选中相关
        val mouseAdapter = TerminalPanelMouseSelectionAdapter(this, terminal)
        this.addMouseListener(mouseAdapter)
        this.addMouseMotionListener(mouseAdapter)

        // 悬浮工具栏
        val floatingToolBarAdapter = TerminalPanelMouseFloatingToolBarAdapter(floatingToolBar, this, terminalDisplay)
        this.addMouseMotionListener(floatingToolBarAdapter)
        this.addMouseListener(floatingToolBarAdapter)

        // 超链接
        val hyperlinkAdapter = TerminalPanelMouseHyperlinkAdapter(this, terminal)
        this.addMouseListener(hyperlinkAdapter)

        // 鼠标跟踪
        val trackingAdapter = TerminalPanelMouseTrackingAdapter(this, terminal, ptyConnector)
        this.addMouseListener(trackingAdapter)
        this.addMouseWheelListener(trackingAdapter)

        // 悬浮窗隐藏/关闭时，重新渲染一次
        floatingToolBar.addPropertyChangeListener { evt ->
            if (evt.propertyName == "State") {
                repaintImmediate()
                println("ASD")
            }
        }

        // 滚动相关
        this.addMouseWheelListener(object : MouseWheelListener {
            override fun mouseWheelMoved(e: MouseWheelEvent) {
                if (!terminal.getScrollingModel().canVerticalScroll()) {
                    return
                }

                val unitsToScroll = e.unitsToScroll
                if (e.isShiftDown || unitsToScroll == 0 || abs(e.preciseWheelRotation) < 0.01) {
                    return
                }
                val value = scrollBar.value + unitsToScroll
                scrollBar.value = value
                terminal.getScrollingModel().scrollTo(value)
            }
        })

        // 监听数据变动然后动态渲染
        terminal.getTerminalModel().addDataListener(TerminalPanelRepaintListener(this))
        terminal.getTerminalModel().addDataListener(object : DataListener {
            override fun onChanged(key: DataKey<*>, data: Any) {
                if (key == ScrollingModel.Scroll) {
                    val rows = terminal.getTerminalModel().getRows()
                    scrollBar.maximum = terminal.getScrollingModel().getMaxVerticalScrollOffset() + rows
                    scrollBar.value = terminal.getScrollingModel().getVerticalScrollOffset()
                    scrollBar.visibleAmount = rows
                }
            }
        })

        scrollBar.addAdjustmentListener { e ->
            if (scrollBar.isVisible && e.valueIsAdjusting) {
                terminal.getScrollingModel().scrollTo(scrollBar.value)
            }
        }

        // 开启拖拽
        enableDropTarget()


    }

    private fun enableDropTarget() {
        dropTarget = object : DropTarget() {
            override fun drop(e: DropTargetDropEvent) {
                if (!dropFiles) {
                    return
                }

                e.acceptDrop(DnDConstants.ACTION_LINK)
                if (!e.transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    return
                }

                val files = (e.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>)
                    .filterIsInstance<File>()
                if (files.isEmpty()) {
                    return
                }

                val sb = StringBuilder()
                for (file in files) {
                    sb.append(file.absolutePath).append(StringUtils.SPACE)
                }

                paste(sb.toString())
            }
        }
    }

    fun toast(text: String, duration: Duration = 500.milliseconds) {
        terminalDisplay.toast(text, duration)
    }

    fun hideToast() {
        terminalDisplay.hideToast()
    }

    /**
     * XY像素点转坐标点
     */
    fun pointToPosition(point: Point): Position {
        return terminalDisplay.pointToPosition(Point(point.x - padding.left, point.y - padding.top))
    }

    fun repaintImmediate() {
        if (terminalDisplay.isShowing) {
            terminalDisplay.repaint()
            scrollBar.repaint()
        }
    }

    fun getTerminalActions(): List<TerminalPredicateAction> {
        return actions
    }

    fun addTerminalAction(action: TerminalPredicateAction) {
        actions.add(action)
    }

    fun removeTerminalAction(action: TerminalPredicateAction) {
        actions.remove(action)
    }

    fun addTerminalPaintListener(listener: TerminalPaintListener) {
        listenerList.add(TerminalPaintListener::class.java, listener)
    }

    fun removeTerminalPaintListener(listener: TerminalPaintListener) {
        listenerList.remove(TerminalPaintListener::class.java, listener)
    }


    override fun getInputMethodRequests(): InputMethodRequests {
        return MyInputMethodRequests()
    }


    override fun processInputMethodEvent(e: InputMethodEvent) {

        terminalDisplay.inputMethodData = TerminalInputMethodData.Default

        val committedCharacterCount = e.committedCharacterCount
        val attributedCharacterIterator = e.text ?: return

        var c = attributedCharacterIterator.first()
        val sb = StringBuilder()
        while (c != CharacterIterator.DONE) {
            // Hack just like in javax.swing.text.DefaultEditorKit.DefaultKeyTypedAction
            if (c.code >= 0x20 && c.code != 0x7F) {
                sb.append(c)
            }
            c = attributedCharacterIterator.next()
        }

        if (sb.isEmpty()) {
            return
        }

        // 输入法提交
        if (committedCharacterCount > 0) {
            ptyConnector.write(sb.toString())
        } else {
            val breakIterator = BreakIterator.getCharacterInstance()
            val chars = mutableListOf<Char>()
            val text = sb.toString()
            breakIterator.setText(text)
            var start = breakIterator.first()
            var end = breakIterator.next()
            val followings = mutableMapOf<Int, Int>()
            while (end != BreakIterator.DONE) {
                val ch = text.substring(start, end)
                chars.addAll(ch.toCharArray().toList())
                if (ch.length == 1 && mk_wcwidth(ch.first()) == 2) {
                    chars.add(Char.SoftHyphen)
                }
                followings[start] = chars.size
                start = end
                end = breakIterator.next()
            }
            // Windows 索引是从 1 开始的
            val charIndex = (e.caret?.charIndex ?: 0) - if (SystemInfo.isWindows) 1 else 0
            terminalDisplay.inputMethodData = TerminalInputMethodData.Default.copy(
                chars = CharBuffer(chars.toCharArray(), TextStyle.Default.underline(true)),
                offset = followings.getOrDefault(charIndex, followings.getOrDefault(charIndex - 1, 0))
            )
        }

    }


    private inner class MyInputMethodRequests : InputMethodRequests {
        private val cursorModel get() = terminal.getCursorModel()

        override fun getTextLocation(e: TextHitInfo?): Rectangle {
            val position = cursorModel.getPosition()
            val rectangle = Rectangle(
                position.x * getAverageCharWidth(),
                position.y * getLineHeight() + if (SystemUtils.IS_OS_WINDOWS) abs(terminalDisplay.getFontMetrics().descent) else 0,
                0, 0
            )
            rectangle.translate(locationOnScreen.x, locationOnScreen.y)
            return rectangle
        }

        override fun getLocationOffset(x: Int, y: Int): TextHitInfo? {
            return null
        }

        override fun getInsertPositionOffset(): Int {
            return 0
        }

        override fun getCommittedText(
            beginIndex: Int,
            endIndex: Int,
            attributes: Array<out AttributedCharacterIterator.Attribute>?
        ): AttributedCharacterIterator {
            return AttributedString(String()).iterator
        }

        override fun getCommittedTextLength(): Int {
            return 0
        }

        override fun cancelLatestCommittedText(attributes: Array<out AttributedCharacterIterator.Attribute>?): AttributedCharacterIterator? {
            return null
        }

        override fun getSelectedText(attributes: Array<out AttributedCharacterIterator.Attribute>?): AttributedCharacterIterator? {
            return null
        }

    }


    fun getAverageCharWidth(): Int {
        return terminalDisplay.getAverageCharWidth()
    }

    fun getLineHeight(): Int {
        return terminalDisplay.getLineHeight()
    }

    fun showFind() {
        terminalFindPanel.isVisible = true
        terminal.getTerminalModel().setData(Finding, true)
        SwingUtilities.invokeLater { terminalFindPanel.textField.requestFocusInWindow() }
    }

    fun hideFind() {
        terminalFindPanel.isVisible = false
        terminal.getTerminalModel().setData(Finding, false)
        SwingUtilities.invokeLater { requestFocusInWindow() }
    }

    /**
     * 执行粘贴操作
     */
    fun paste(text: String) {
        val content = if (SystemInfo.isWindows) {
            text.replace("${ControlCharacters.CR}${ControlCharacters.LF}", "${ControlCharacters.LF}")
        } else {
            text.replace(ControlCharacters.LF, ControlCharacters.CR)
        }

        if (terminal.getTerminalModel().getData(DataKey.BracketedPasteMode, false)) {
            ptyConnector.write("${ControlCharacters.ESC}[200~${content}${ControlCharacters.ESC}[201~")
        } else {
            ptyConnector.write(content)
        }

        terminal.getScrollingModel().scrollToRow(
            terminal.getDocument().getCurrentTerminalLineBuffer().getBufferCount()
                    + terminal.getCursorModel().getPosition().y
        )

        terminal.getSelectionModel().clearSelection()

    }

    /**
     * 返回选中的文本
     */
    fun copy(): String {
        return terminal.getSelectionModel().getSelectedText()
    }

    fun winSize(): TerminalSize {
        val cols = terminalDisplay.width / getAverageCharWidth()
        val rows = terminalDisplay.height / getLineHeight()
        return TerminalSize(rows, cols)
    }

    override fun paint(g: Graphics) {
        background = Color(
            terminal.getTerminalModel().getColorPalette()
                .getColor(TerminalColor.Basic.BACKGROUND)
        )
        super.paint(g)
    }

    private inner class TerminalLayeredPane : JLayeredPane() {
        override fun doLayout() {
            val averageCharWidth = getAverageCharWidth()
            synchronized(treeLock) {
                val w = width
                val h = height
                val findPanelHeight = max(terminalFindPanel.preferredSize.height, terminalFindPanel.height)
                val floatingComponent = floatingToolBar.getJComponent()

                for (c in components) {
                    when (c) {
                        terminalDisplay -> {
                            c.setBounds(
                                padding.left,
                                padding.top,
                                w - padding.right - padding.left,
                                h - padding.bottom - padding.top
                            )
                        }

                        terminalFindPanel -> {
                            val width = averageCharWidth * 35
                            c.setBounds(
                                w - width,
                                0,
                                width,
                                findPanelHeight
                            )
                        }

                        floatingComponent -> {
                            val width = floatingComponent.preferredSize.width
                            val y = 4
                            c.setBounds(
                                w - width,
                                if (terminalFindPanel.isVisible) findPanelHeight + y else y,
                                width,
                                max(floatingComponent.preferredSize.height, floatingComponent.height)
                            )
                        }
                    }
                }
            }
        }
    }
}