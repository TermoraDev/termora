package app.termora.terminal.panel

import app.termora.actions.AnActionEvent
import app.termora.actions.TerminalCopyAction
import app.termora.actions.TerminalPasteAction
import app.termora.database.DatabaseManager
import app.termora.terminal.*
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.action.ActionManager
import org.slf4j.LoggerFactory
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.BreakIterator
import javax.swing.SwingUtilities
import kotlin.math.abs

class TerminalPanelMouseSelectionAdapter(private val terminalPanel: TerminalPanel, private val terminal: Terminal) :
    MouseAdapter() {

    private val terminalModel get() = terminal.getTerminalModel()
    private val isMouseTracking
        get() = terminalModel.getData(
            DataKey.MouseMode,
            MouseMode.MOUSE_REPORTING_NONE
        ) != MouseMode.MOUSE_REPORTING_NONE
    private val isSelectCopy get() = terminalModel.getData(TerminalPanel.SelectCopy, false)
    private val selectionModel get() = terminal.getSelectionModel()
    private val wordBreakIterator = BreakIterator.getWordInstance()
    private val rightClickMode get() = DatabaseManager.getInstance().terminal.rightClick

    companion object {
        private val log = LoggerFactory.getLogger(TerminalPanelMouseSelectionAdapter::class.java)
    }

    private var mousePressedPoint = Point(0, 0)

    private object DragSelection {
        var dragging = false
        var start = Position.unknown
        var end = Position.unknown
    }

    override fun mousePressed(e: MouseEvent) {

        terminalPanel.requestFocusInWindow()

        if (isMouseTracking) {
            return
        }

        if (SwingUtilities.isRightMouseButton(e)) {
            // 如果有选中并且开启了选中复制，那么右键直接是粘贴
            if (selectionModel.hasSelection() && isSelectCopy.not()) {
                if (rightClickMode != "Nothing") {
                    triggerCopyAction(
                        KeyEvent(
                            e.component,
                            KeyEvent.KEY_PRESSED,
                            e.`when`,
                            e.modifiersEx,
                            KeyEvent.VK_C,
                            'C'
                        )
                    )

                    if (rightClickMode == "CopyAndPaste") {
                        triggerPasteAction(
                            KeyEvent(
                                e.component,
                                KeyEvent.KEY_PRESSED,
                                e.`when`,
                                e.modifiersEx,
                                KeyEvent.VK_V,
                                'V'
                            )
                        )
                    }
                }
            } else {
                // paste
                triggerPasteAction(
                    KeyEvent(
                        e.component,
                        KeyEvent.KEY_PRESSED,
                        e.`when`,
                        e.modifiersEx,
                        KeyEvent.VK_V,
                        'V'
                    )
                )
            }
        } else if (SwingUtilities.isLeftMouseButton(e)) {
            mousePressedPoint.x = e.x
            mousePressedPoint.y = e.y
        }


        // 如果只有 Shift 键按下，那么应该追加选中
        if (selectionModel.hasSelection() && SwingUtilities.isLeftMouseButton(e) && e.modifiersEx == 1088) {
            val position = terminalPanel.pointToPosition(e.point)
            val selectionStartPosition = selectionModel.getSelectionStartPosition()
            val selectionEndPosition = selectionModel.getSelectionEndPosition()
            val cols = terminalModel.getCols()
            val clickIndex = position.y * cols + position.x
            val startIndex = selectionStartPosition.y * cols + selectionStartPosition.x
            val endIndex = selectionEndPosition.y * cols + selectionEndPosition.x
            val startDiff = abs(clickIndex - startIndex)
            val endDiff = abs(clickIndex - endIndex)
            // 距离哪个最近就用哪一个
            if (startDiff < endDiff) {
                selectionModel.setSelection(position, selectionEndPosition)
            } else {
                selectionModel.setSelection(selectionStartPosition, position)
            }
        } else {
            selectionModel.clearSelection()
        }

        if (SwingUtilities.isLeftMouseButton(e)) {
            if (e.clickCount % 2 == 0) {
                selectWord(terminalPanel.pointToPosition(e.point))
            } else if (e.clickCount % 3 == 0) {
                selectLine(terminalPanel.pointToPosition(e.point))
            }
        }

    }

    override fun mouseReleased(e: MouseEvent) {
        if (DragSelection.dragging) {
            endSelect(Position(x = e.x, y = e.y))
        }
    }

    override fun mouseDragged(e: MouseEvent) {
        // 如果开启了鼠标追踪，那么就不支持选择功能了
        if (isMouseTracking) {
            return
        }

        if (!SwingUtilities.isLeftMouseButton(e)) {
            return
        }

        if (DragSelection.dragging) {
            select(Position(x = e.x, y = e.y))
        } else {
            // 有的时候会太灵敏，这里容错一下
            // 如果不判断的话可能会导致移动了一点点就就进入选择状态了
            val diff = terminalPanel.getAverageCharWidth() / 5.0
            if (abs(mousePressedPoint.y - e.y) >= diff || abs(mousePressedPoint.x - e.x) >= diff) {
                // 设置选中模式
                terminal.getSelectionModel().setBlockSelection(isOnlyAltDown(e))
                beginSelect(
                    Position(x = mousePressedPoint.x, y = mousePressedPoint.y),
                )
            }
        }
    }

    private fun isOnlyAltDown(e: MouseEvent): Boolean {
        return e.isAltDown &&
                e.isMetaDown.not() &&
                e.isControlDown.not() &&
                e.isShiftDown.not() &&
                e.isAltGraphDown.not()
    }

    private fun beginSelect(position: Position) {

        if (DragSelection.dragging) {
            throw IllegalStateException("Selecting")
        }

        selectionModel.clearSelection()

        DragSelection.dragging = true
        DragSelection.start = terminalPanel.pointToPosition(Point(position.x, position.y))

        if (log.isTraceEnabled) {
            log.trace("Begin select start={}", DragSelection.start)
        }
    }

    private fun select(position: Position) {

        if (!DragSelection.dragging) {
            throw IllegalStateException("Not Selecting")
        }

        val point = terminalPanel.pointToPosition(Point(position.x, position.y))

        DragSelection.end = point
        var start = DragSelection.start
        var end = DragSelection.end

        terminal.getScrollingModel().scrollToRow(point.y)

        // 判断是否反了
        if (start.y > end.y || (start.y == end.y && start.x > end.x)) {
            val temp = start
            start = end
            end = temp
        }

        terminal.getSelectionModel().setSelection(start, end)

        if (log.isTraceEnabled) {
            log.trace("Select start={} end={}", start, end)
        }
    }

    private fun endSelect(position: Position) {
        if (!DragSelection.dragging) {
            throw IllegalStateException("Not Selecting")
        }

        // 最后选择一次
        select(position)

        DragSelection.dragging = false

        // 如果开启了选中复制
        if (isSelectCopy) {
            triggerCopyAction()
        }

        if (log.isTraceEnabled) {
            log.trace("End select start={} end={}", DragSelection.start, DragSelection.end)
        }
    }

    private fun triggerCopyAction(
        e: KeyEvent = KeyEvent(
            terminalPanel,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_C,
            'C'
        )
    ) {
        ActionManager.getInstance()
            .getAction(TerminalCopyAction.COPY)
            ?.actionPerformed(AnActionEvent(terminalPanel, StringUtils.EMPTY, e))
    }

    private fun triggerPasteAction(
        e: KeyEvent = KeyEvent(
            terminalPanel,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_V,
            'V'
        )
    ) {
        ActionManager.getInstance()
            .getAction(TerminalPasteAction.PASTE)
            ?.actionPerformed(AnActionEvent(terminalPanel, StringUtils.EMPTY, e))
    }

    private fun selectWord(position: Position) {
        val document = terminal.getDocument()
        if (!position.isValid() || position.y > document.getLineCount()) {
            return
        }

        val line = document.getLine(position.y)
        val text = line.getText()
        if (text.isEmpty()) {
            selectLine(position)
            return
        }

        for ((offset, _, end) in convertWords(text)) {
            if (position.x in offset..end) {
                selectionModel.setSelection(
                    Position(y = position.y, x = offset + 1),
                    Position(y = position.y, x = end)
                )
                if (isSelectCopy) {
                    triggerCopyAction()
                }
                return
            }
        }

        val actualCount = line.actualCount()
        if (position.x > actualCount) {
            selectionModel.setSelection(
                Position(y = position.y, x = actualCount + 1),
                Position(y = position.y, x = terminalModel.getCols())
            )
        }

    }

    private fun selectLine(position: Position) {
        val document = terminal.getDocument()
        if (!position.isValid() || position.y > document.getLineCount()) {
            return
        }

        selectionModel.setSelection(
            Position(y = position.y, x = 1),
            Position(y = position.y, x = terminalModel.getCols())
        )

        if (isSelectCopy) {
            triggerCopyAction()
        }
    }

    /**
     * @return Triple<StartOffset, Text, EndOffset>
     */
    private fun convertWords(text: String): List<Triple<Int, String, Int>> {
        val words = mutableListOf<Triple<Int, String, Int>>()
        wordBreakIterator.setText(text)

        var doubleWidthCharCount = 0
        var start = wordBreakIterator.first()
        var end = wordBreakIterator.next()
        while (end != BreakIterator.DONE) {

            val word = text.substring(start, end)

            // 因为中文字符占用两个字宽，但是 text 是不包含 ZeroWidth 字符的，所以这里要加上
            val first = doubleWidthCharCount == 0
            val widthDiff = getStringWidth(word) - word.length

            words.add(
                Triple(
                    start + (if (first) 0 else doubleWidthCharCount),
                    word,
                    end + (doubleWidthCharCount + widthDiff)
                )
            )

            doubleWidthCharCount += getStringWidth(word) - word.length

            start = end
            end = wordBreakIterator.next()
        }

        return words
    }

    private fun getStringWidth(text: String): Int {
        var count = 0
        text.toCharArray().forEach { count += mk_wcwidth(it) }
        return count
    }
}