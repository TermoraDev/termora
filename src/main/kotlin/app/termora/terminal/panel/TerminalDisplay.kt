package app.termora.terminal.panel

import app.termora.DynamicColor
import app.termora.assertEventDispatchThread
import app.termora.database.DatabaseManager
import app.termora.swingCoroutineScope
import app.termora.terminal.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import java.awt.*
import javax.swing.JComponent
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration


class TerminalDisplay(
    private val terminalPanel: TerminalPanel,
    private val terminal: Terminal,
    private val terminalBlink: TerminalBlink
) : JComponent() {

    companion object {
        private val lru = object : LinkedHashMap<String, Boolean>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > 2048
            }
        }
    }

    private val debug get() = terminalPanel.debug
    private val colorPalette get() = terminal.getTerminalModel().getColorPalette()
    private val toaster = Toaster()

    private var font = getTerminalFont()
    private var monospacedFont = Font(Font.MONOSPACED, font.style, font.size)
    private var boldFont = font.deriveFont(Font.BOLD)
    private var italicFont = font.deriveFont(Font.ITALIC)
    private var boldItalicFont = font.deriveFont(Font.ITALIC or Font.BOLD)

    /**
     * 正在输入的内容
     */
    var inputMethodData = TerminalInputMethodData.Default
        set(value) {
            field = value
            terminalPanel.repaintImmediate()
        }

    init {
        terminalPanel.addTerminalPaintListener(toaster)
        putClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        cursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)
    }

    override fun paint(g: Graphics) {
        if (g is Graphics2D) {
            setupAntialiasing(g)
            clear(g)

            // 渲染之前
            drawBefore(g)
            // 渲染字符
            drawCharacters(g)
            // 渲染基础线
            drawBaseline(g)
            // 渲染行号
            drawLineNumber(g)
            // 渲染之后
            drawAfter(g)
        }
    }

    private fun setupAntialiasing(graphics: Graphics) {
        if (graphics is Graphics2D) {
            graphics.setRenderingHints(
                RenderingHints(
                    RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON
                )
            )
        }
    }

    private fun drawBefore(g: Graphics) {
        terminalPanel.getListeners(TerminalPaintListener::class.java)
            .forEach {
                it.before(
                    terminal.getScrollingModel().getVerticalScrollOffset(),
                    terminal.getTerminalModel().getRows(),
                    g,
                    terminalPanel,
                    this,
                    terminal
                )
            }
    }

    private fun drawAfter(g: Graphics) {
        terminalPanel.getListeners(TerminalPaintListener::class.java)
            .forEach {
                it.after(
                    terminal.getScrollingModel().getVerticalScrollOffset(),
                    terminal.getTerminalModel().getRows(),
                    g,
                    terminalPanel,
                    this,
                    terminal
                )
            }
    }

    private fun drawBaseline(g: Graphics) {
        if (!debug) return

        val lineHeight = getLineHeight()
        val averageCharWidth = getAverageCharWidth()
        g.color = DynamicColor.BorderColor

        for (i in 1..height / lineHeight) {
            g.drawLine(0, i * lineHeight, width, i * lineHeight)
        }

        for (i in 1..width / averageCharWidth) {
            g.drawLine(i * averageCharWidth, 0, i * averageCharWidth, height)
        }
    }


    private fun clear(g: Graphics) {
        g.font = font
        g.color = Color(colorPalette.getColor(TerminalColor.Basic.BACKGROUND))
        g.fillRect(0, 0, width, height)
    }

    private fun drawCursor(g: Graphics, y: Int, xOffset: Int, width: Int) {
        val lineHeight = getLineHeight()
        val style = if (inputMethodData.isNoTyping)
            terminal.getTerminalModel().getData(DataKey.CursorStyle) else CursorStyle.Bar
        val hasFocus = terminal.getTerminalModel().getData(TerminalPanel.Focused, false)

        // background
        g.color = Color(colorPalette.getColor(TerminalColor.Cursor.BACKGROUND))

        if (style == CursorStyle.Block) {
            if (hasFocus) {
                g.fillRect(xOffset, (y - 1) * lineHeight, width, lineHeight)
            } else {
                g.drawRect(xOffset, (y - 1) * lineHeight, width, lineHeight)
            }
        } else if (style == CursorStyle.Underline) {
            val h = ceil(lineHeight / 10.0).toInt()
            g.fillRect(xOffset, y * lineHeight - h / 2, width, h)
        } else if (style == CursorStyle.Bar) {
            if (inputMethodData.isTyping) {
                val w = ceil(width / 3.5).toInt()
                g.fillRect(xOffset, (y - 1) * lineHeight, w, lineHeight)
            } else {
                g.drawLine(xOffset, y * lineHeight - lineHeight, xOffset, y * lineHeight)
            }
        }
    }

    fun getAverageCharWidth(): Int {
        checkFont()
        return getFontMetrics().charWidth('W')
    }

    fun getLineHeight(): Int {
        checkFont()
        return getFontMetrics().height
    }

    fun getFontMetrics(): FontMetrics {
        return getFontMetrics(font)
    }

    private fun checkFont() {
        // 如果字体已经改变，那么这里刷新字体
        if (font.family != DatabaseManager.getInstance().terminal.font
            || font.size != DatabaseManager.getInstance().terminal.fontSize
        ) {
            font = getTerminalFont()
            monospacedFont = Font(Font.MONOSPACED, font.style, font.size)
            boldFont = font.deriveFont(Font.BOLD)
            italicFont = font.deriveFont(Font.ITALIC)
            boldItalicFont = font.deriveFont(Font.ITALIC or Font.BOLD)
        }
    }

    /**
     * XY像素点转坐标点
     */
    fun pointToPosition(point: Point): Position {
        val pointY = point.y
        val pointX = point.x

        // 获取第几行
        val y = max(
            1, (ceil(1.0 * pointY / (getLineHeight())) + terminal.getScrollingModel()
                .getVerticalScrollOffset()).toInt()
        )

        var x = max(1, ceil(1.0 * pointX / (getAverageCharWidth())).toInt())

        if (y <= terminal.getDocument().getLineCount()) {
            val line = terminal.getDocument().getLine(y)
            if (x <= line.chars().size) {
                // 如果选择的是零宽字符，表示前面是中文
                if (line.chars()[x - 1].first == Char.SoftHyphen) {
                    x--
                }
            }
        }


        // 行和列是从 1 开始的
        return Position(x = x, y = y)
    }

    private fun drawCharacters(g: Graphics2D) {
        val terminalModel = terminal.getTerminalModel()
        val reverseVideo = terminalModel.getData(DataKey.ReverseVideo, false)
        val rows = terminalModel.getRows()
        val cols = terminalModel.getCols()
        val triple = Triple(Char.Space.toString(), TextStyle.Default, 1)
        val cursorPosition = terminal.getCursorModel().getPosition()
        val averageCharWidth = getAverageCharWidth()
        val maxVerticalScrollOffset = terminal.getScrollingModel().getMaxVerticalScrollOffset()
        val verticalScrollOffset = terminal.getScrollingModel().getVerticalScrollOffset()
        val selectionModel = terminal.getSelectionModel()
        val cursorStyle = terminalModel.getData(DataKey.CursorStyle)
        val showCursor = terminalModel.getData(DataKey.ShowCursor)
        val markupModel = terminal.getMarkupModel()
        val lineHeight = getLineHeight()
        val blink = terminalBlink.blink
        val cursorBlink = terminalBlink.cursorBlink
        val hasFocus = terminalModel.getData(TerminalPanel.Focused, false)


        for (i in 1..rows) {
            var xOffset = 0
            val row = verticalScrollOffset + i - 1
            val characters = smartCharacters(row).iterator()
            var j = 1
            while (j <= cols) {
                val position = Position(row + 1, j)
                val caret = showCursor && j == cursorPosition.x + inputMethodData.offset
                        && i == cursorPosition.y + (maxVerticalScrollOffset - verticalScrollOffset)

                val (text, style, length) = if (characters.hasNext()) characters.next() else triple
                var textStyle = style
                val hasSelection = selectionModel.hasSelection(y = i + verticalScrollOffset, x = j)
                var background = getDisplayColor(style.background, TerminalColor.Basic.BACKGROUND)
                var foreground = getDisplayColor(style.foreground, TerminalColor.Basic.FOREGROUND)

                // 颜色反转
                if (reverseVideo || style.inverse) {
                    val tmp = foreground
                    foreground = background
                    background = tmp
                }

                // 荧光笔
                for (highlighter in markupModel.getHighlighters(position)) {
                    val highlighterStyle = highlighter.getTextStyle(position, textStyle)
                    foreground = getDisplayColor(highlighterStyle.foreground, TerminalColor.Basic.FOREGROUND)
                    background = getDisplayColor(highlighterStyle.background, TerminalColor.Basic.BACKGROUND)
                    textStyle = highlighterStyle
                }

                // 选中的样式优先级最高
                if (hasSelection) {
                    foreground = colorPalette.getColor(TerminalColor.Basic.SELECTION_FOREGROUND)
                    background = colorPalette.getColor(TerminalColor.Basic.SELECTION_BACKGROUND)
                }

                // 如果启用了闪烁
                if (textStyle.blink) {
                    if (!blink) {
                        continue
                    }
                }

                // 设置字体
                g.font = getDisplayFont(text, textStyle)
                val charWidth = min(
                    max(g.fontMetrics.stringWidth(text), length * averageCharWidth),
                    length * averageCharWidth
                )

                // 如果没有颜色反转并且与渲染的背景色一致，那么无需渲染背景
                if (textStyle.inverse || background != colorPalette.getColor(TerminalColor.Basic.BACKGROUND)) {
                    g.color = Color(background)
                    g.fillRect(xOffset, (i - 1) * lineHeight, charWidth, lineHeight)
                }

                // 前景色
                g.color = if (textStyle.dim) Color(foreground).darker() else Color(foreground)

                // 下划线
                if (textStyle.underline) {
                    val stroke = g.stroke
                    val width = if (stroke is BasicStroke) stroke.lineWidth.toInt() else 0
                    g.drawLine(xOffset, i * lineHeight - width, xOffset + charWidth, i * lineHeight - width)
                }

                // 删除线
                if (textStyle.lineThrough) {
                    val ly = i * lineHeight - lineHeight / 2
                    g.drawLine(xOffset, ly, xOffset + charWidth, ly)
                }

                // 双下划线
                if (textStyle.doublyUnderline) {
                    if (textStyle.underline) {
                        g.drawLine(xOffset, i * lineHeight - 3, xOffset + charWidth, i * lineHeight - 3)
                    } else {
                        g.drawLine(xOffset, i * lineHeight, xOffset + charWidth, i * lineHeight)
                        g.drawLine(xOffset, i * lineHeight - 3, xOffset + charWidth, i * lineHeight - 3)
                    }
                }

                // 渲染光标
                if (caret) {
                    // 这几种情况光标才会渲染：输入中、闪烁中、没有焦点
                    if (inputMethodData.isTyping || cursorBlink || !hasFocus) {
                        drawCursor(g, i, xOffset, charWidth)
                        // 如果是获取焦点状态，那么颜色互换
                        if (hasFocus && cursorStyle == CursorStyle.Block && inputMethodData.isNoTyping) {
                            g.color = Color(colorPalette.getColor(TerminalColor.Basic.BACKGROUND))
                        } else {
                            g.color = Color(foreground)
                        }
                    }
                }

                // 渲染文本
                g.drawString(text, xOffset, i * lineHeight - g.fontMetrics.descent)

                // 偏移量
                xOffset += charWidth

                // 多字符文字
                j += length
            }
        }
    }

    /**
     * 融合输入法
     */
    private fun smartCharacters(row: Int): List<Triple<String, TextStyle, Int>> {
        val buffer = terminal.getDocument().getCurrentTerminalLineBuffer()
        val line = buffer.getLineAt(row)
        val characters = line.characters()

        if (inputMethodData.isNoTyping) {
            return characters
        }

        val position = terminal.getCursorModel().getPosition()
        // 光标行也就是输入行
        if (position.y + terminal.getScrollingModel().getVerticalScrollOffset() - 1 == row) {
            val imeLine = TerminalLine()
            imeLine.write(0, inputMethodData.chars)
            var index = 0
            for (i in 0 until characters.size) {
                index += characters[i].third
                if (index >= position.x) {
                    characters.addAll(i, imeLine.characters())
                    break
                }
            }
        }

        return characters
    }

    private fun getDisplayColor(color: Int, terminalColor: TerminalColor): Int {
        if (color == 0) {
            return colorPalette.getColor(terminalColor)
        }

        if (color <= 16) {
            return colorPalette.getXTerm256Color(color)
        }
        return color
    }


    fun getDisplayFont(text: String, style: TextStyle): Font {
        assertEventDispatchThread()

        var font = if (style.bold && style.italic) {
            boldItalicFont
        } else if (style.italic) {
            italicFont
        } else if (style.bold) {
            boldFont
        } else {
            font
        }

        val key = "${font.fontName}:${font.style}:${font.size}:${text}"
        if (lru.containsKey(key)) {
            if (!lru.getValue(key)) {
                font = monospacedFont
            }
        } else {
            if ((font.canDisplayUpTo(text) != -1).also { lru[key] = !it }) {
                font = monospacedFont
            }
        }


        return font
    }

    private fun drawLineNumber(g: Graphics) {
        if (!debug) return

        g.color = DynamicColor("Component.warning.focusedBorderColor")
        val fontMetrics = getFontMetrics()
        val lineHeight = getLineHeight()
        val verticalScrollOffset = terminal.getScrollingModel().getVerticalScrollOffset()


        for (i in 1..height / lineHeight) {
            val text = "${verticalScrollOffset + i}"
            val w = fontMetrics.stringWidth(text)
            g.drawString(text, width - w, i * lineHeight - fontMetrics.descent)
        }
    }


    private fun getTerminalFont(): Font {
        return Font(
            DatabaseManager.getInstance().terminal.font,
            Font.PLAIN,
            DatabaseManager.getInstance().terminal.fontSize
        )
    }

    fun toast(text: String, duration: Duration) {
        toaster.toast(text, duration)
    }

    fun hideToast() {
        toaster.hideToast()
    }

    private data class Toast(val text: String)
    private inner class Toaster : TerminalPaintListener {
        private val toasts = mutableListOf<Toast>()

        override fun after(
            offset: Int,
            count: Int,
            g: Graphics,
            terminalPanel: TerminalPanel,
            terminalDisplay: TerminalDisplay,
            terminal: Terminal
        ) {
            if (toasts.isEmpty()) return
            val toast = toasts.last()
            val font = g.font

            g.font = getDisplayFont(toast.text, TextStyle.Default)

            val fontMetrics = g.fontMetrics
            val lineHeight = fontMetrics.height
            val width = fontMetrics.stringWidth(toast.text) + getAverageCharWidth() * 2
            val height = lineHeight + lineHeight / 2
            val x = terminalDisplay.width / 2 - width / 2
            val y = terminalDisplay.height / 2 - height / 2

            g.color = Color(colorPalette.getColor(TerminalColor.Basic.FOREGROUND))
            g.fillRect(x, y, width, height)

            g.color = Color(colorPalette.getColor(TerminalColor.Basic.BACKGROUND))
            g.drawString(toast.text, x + getAverageCharWidth(), y + lineHeight)

            g.font = font
        }

        fun toast(text: String, duration: Duration) {
            if (!terminalPanel.showToast) {
                return
            }

            val toast = Toast(text)
            swingCoroutineScope.launch(Dispatchers.Swing) {
                delay(duration)
                toasts.remove(toast)
                terminalPanel.repaintImmediate()
            }
            toasts.add(toast)
            terminalPanel.repaintImmediate()
        }

        fun hideToast() {
            toasts.clear()
            terminalPanel.repaintImmediate()
        }
    }


}