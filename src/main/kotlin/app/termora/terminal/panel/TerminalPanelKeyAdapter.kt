package app.termora.terminal.panel

import app.termora.actions.TerminalCopyAction
import app.termora.keymap.KeyShortcut
import app.termora.keymap.KeymapManager
import app.termora.terminal.ControlCharacters
import app.termora.terminal.Terminal
import com.formdev.flatlaf.util.SystemInfo
import org.slf4j.LoggerFactory
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class TerminalPanelKeyAdapter(
    private val terminalPanel: TerminalPanel,
    private val terminal: Terminal,
    private val writer: TerminalWriter
) : KeyAdapter() {

    companion object {
        private const val ASCII_ESC = 27.toChar()
        private val log = LoggerFactory.getLogger(TerminalPanelKeyAdapter::class.java)
    }

    private val activeKeymap get() = KeymapManager.getInstance().getActiveKeymap()
    private var isIgnoreKeyTyped = false

    override fun keyTyped(e: KeyEvent) {
        // 如果忽略并且不是正常字符
        if (isIgnoreKeyTyped || Character.isISOControl(e.keyChar)) {
            return
        }

        terminal.getSelectionModel().clearSelection()
        writer.write(TerminalWriter.WriteRequest.fromBytes("${e.keyChar}".toByteArray(writer.getCharset())))
        terminal.getScrollingModel().scrollTo(Int.MAX_VALUE)

    }

    override fun keyPressed(e: KeyEvent) {
        // 重置
        isIgnoreKeyTyped = false

        try {
            // 处理
            doKeyPressed(e)
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
            // https://github.com/TermoraDev/termora/issues/388
            terminal.getSelectionModel().clearSelection()
            terminal.getScrollingModel().scrollTo(Int.MAX_VALUE)
            return
        }

        // 如果已经处理，那么忽略 keyTyped 事件
        if (e.isConsumed) {
            isIgnoreKeyTyped = true
        }
    }

    private fun doKeyPressed(e: KeyEvent) {
        if (e.isConsumed) return

        // remove all toast
        if (e.keyCode == KeyEvent.VK_ESCAPE) {
            terminalPanel.hideToast()
        }

        val keyStroke = KeyStroke.getKeyStrokeForEvent(e)
        val keymapActions = activeKeymap.getActionIds(KeyShortcut(keyStroke))
        for (action in terminalPanel.getTerminalActions()) {
            if (action.test(keyStroke, e)) {
                action.actionPerformed(e)
                return
            }
        }

        val encode = terminal.getKeyEncoder().encode(AWTTerminalKeyEvent(e))
        if (encode.isNotEmpty()) {
            writer.write(TerminalWriter.WriteRequest.fromBytes(encode.toByteArray(writer.getCharset())))
            // scroll to bottom
            terminal.getScrollingModel().scrollTo(Int.MAX_VALUE)
            e.consume()
        }

        // https://github.com/TermoraDev/termora/issues/52
        if (SystemInfo.isWindows && e.keyCode == KeyEvent.VK_TAB && isCtrlPressedOnly(e)) {
            return
        }

        // https://github.com/TermoraDev/termora/issues/331
        if (isAltPressedOnly(e) && Character.isDefined(e.keyChar)) {
            val c = String(charArrayOf(ASCII_ESC, simpleMapKeyCodeToChar(e)))
            writer.write(TerminalWriter.WriteRequest.fromBytes(c.toByteArray(writer.getCharset())))
            // scroll to bottom
            terminal.getScrollingModel().scrollTo(Int.MAX_VALUE)
            e.consume()
            return
        }

        // 如果命中了全局快捷键，那么不处理
        val copyShortcutWithoutSelection =
            keymapActions.contains(TerminalCopyAction.COPY) && terminal.getSelectionModel().hasSelection().not()
        if (keyStroke.modifiers != 0 && keymapActions.isNotEmpty() && !copyShortcutWithoutSelection) {
            return
        }

        val keyChar = mapKeyChar(e)
        if (Character.isISOControl(keyChar)) {
            terminal.getSelectionModel().clearSelection()
            // 如果不为空表示已经发送过了，所以这里为空的时候再发送
            if (encode.isEmpty()) {
                writer.write(TerminalWriter.WriteRequest.fromBytes("$keyChar".toByteArray(writer.getCharset())))
                e.consume()
            }
            terminal.getScrollingModel().scrollTo(Int.MAX_VALUE)
        }

    }

    private fun mapKeyChar(e: KeyEvent): Char {
        if (Character.isISOControl(e.keyChar)) {
            return e.keyChar
        }

        val isCtrlPressedOnly = isCtrlPressedOnly(e)

        // https://github.com/TermoraDev/termora/issues/478
        if (isCtrlPressedOnly && e.keyCode == KeyEvent.VK_OPEN_BRACKET) {
            return ControlCharacters.ESC
        }

        return e.keyChar
    }

    private fun isCtrlPressedOnly(e: KeyEvent): Boolean {
        val modifiersEx = e.modifiersEx
        return (modifiersEx and InputEvent.ALT_DOWN_MASK) == 0
                && (modifiersEx and InputEvent.ALT_GRAPH_DOWN_MASK) == 0
                && (modifiersEx and InputEvent.CTRL_DOWN_MASK) != 0
                && (modifiersEx and InputEvent.SHIFT_DOWN_MASK) == 0
    }

    private fun isAltPressedOnly(e: KeyEvent): Boolean {
        val modifiersEx = e.modifiersEx
        return (modifiersEx and InputEvent.ALT_DOWN_MASK) != 0
                && (modifiersEx and InputEvent.ALT_GRAPH_DOWN_MASK) == 0
                && (modifiersEx and InputEvent.CTRL_DOWN_MASK) == 0
                && (modifiersEx and InputEvent.SHIFT_DOWN_MASK) == 0
    }


    private fun simpleMapKeyCodeToChar(e: KeyEvent): Char {
        // zsh requires proper case of letter
        if (e.isShiftDown) return Character.toUpperCase(e.keyCode.toChar())
        return Character.toLowerCase(e.keyCode.toChar())
    }

}
