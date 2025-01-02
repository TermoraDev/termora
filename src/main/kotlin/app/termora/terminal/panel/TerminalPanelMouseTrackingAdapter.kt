package app.termora.terminal.panel

import app.termora.terminal.*
import org.slf4j.LoggerFactory
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import kotlin.math.abs

class TerminalPanelMouseTrackingAdapter(
    private val terminalPanel: TerminalPanel,
    private val terminal: Terminal,
    private val ptyConnector: PtyConnector
) : MouseAdapter() {

    companion object {
        private val log = LoggerFactory.getLogger(TerminalPanelMouseTrackingAdapter::class.java)
    }

    private val terminalModel get() = terminal.getTerminalModel()
    private val mouseMode get() = terminalModel.getData(DataKey.MouseMode, MouseMode.MOUSE_REPORTING_NONE)
    private val isNotMouseTracking get() = mouseMode == MouseMode.MOUSE_REPORTING_NONE
    private val isUrxvtMouseMode get() = terminalModel.getData(DataKey.urxvtMouseMode, false)
    private val isUTF8MouseMode get() = terminalModel.getData(DataKey.UTF8MouseMode, false)
    private val isSGRMouseMode get() = terminalModel.getData(DataKey.SGRMouseMode, false)
    private val shouldSendMouseData
        get() = mouseMode == MouseMode.MOUSE_REPORTING_NORMAL
                || mouseMode == MouseMode.MOUSE_REPORTING_BUTTON_MOTION
                || mouseMode == MouseMode.MOUSE_REPORTING_ALL_MOTION

    override fun mousePressed(e: MouseEvent) {
        if (isNotMouseTracking) {
            return
        }
        if (shouldSendMouseData) {
            sendMouseEvent(
                terminalPanel.pointToPosition(e.point),
                AWTTerminalMouseEvent(e),
                TerminalMouseEventType.Pressed
            )
        }
    }


    override fun mouseReleased(e: MouseEvent) {
        if (isNotMouseTracking) {
            return
        }
        if (shouldSendMouseData) {
            sendMouseEvent(
                terminalPanel.pointToPosition(e.point),
                AWTTerminalMouseEvent(e),
                TerminalMouseEventType.Released
            )
        }
    }

    override fun mouseMoved(e: MouseEvent) {
        if (mouseMode == MouseMode.MOUSE_REPORTING_ALL_MOTION) {
            val p = terminalPanel.pointToPosition(e.point)
            // release - for 1000/1005/1015 mode
            mouseReport(3, p.x, p.y)
        }
    }

    override fun mouseWheelMoved(e: MouseWheelEvent) {
        if (shouldSendMouseData) {
            val unitsToScroll = e.unitsToScroll
            val encode = terminal.getKeyEncoder()
                .encode(TerminalKeyEvent(if (e.wheelRotation < 0) KeyEvent.VK_UP else KeyEvent.VK_DOWN))

            for (i in 0 until abs(unitsToScroll)) {
                ptyConnector.write(encode)
            }
        }
    }

    private fun sendMouseEvent(position: Position, event: TerminalMouseEvent, eventType: TerminalMouseEventType) {
        if (event.button == TerminalMouseButton.None) {
            return
        }

        if (isNotMouseTracking) {
            return
        }

        var cb = event.button.code
        if (eventType == TerminalMouseEventType.Pressed) {
            if (event.button == TerminalMouseButton.ScrollDown || event.button == TerminalMouseButton.ScrollUp) {
                val offset = TerminalMouseButton.ScrollDown.code
                cb -= offset
                // scroll flag
                //  64 - this is scroll event
                cb = cb or 64
            }
            cb = cb or event.modifiers
        } else if (eventType == TerminalMouseEventType.Released) {
            cb = if (isSGRMouseMode) {
                // for SGR 1006 style, internal use only
                //  128 - mouse button is released
                cb or 128
            } else {
                // release - for 1000/1005/1015 mode
                cb or 3
            }
            cb = cb or event.modifiers
        }

        mouseReport(cb, position.x, position.y)

    }

    private fun mouseReport(cb: Int, x: Int, y: Int) {
        val sb = StringBuilder()
        var charset = Charsets.UTF_8

        if (isUTF8MouseMode) {
            sb.append(ControlCharacters.ESC).append("[M")
                .append((32 + cb).toChar()).append((32 + x).toChar())
                .append((32 + y).toChar())
        } else if (isSGRMouseMode) {
            // for SGR 1006 style, internal use only
            //  128 - mouse button is released
            if ((cb and 128) != 0) {
                // for mouse release event
                sb.append(ControlCharacters.ESC).append("[<")
                    .append(cb xor 128).append(';').append(x).append(';')
                    .append(y).append('m')
            } else {
                // for mouse press/motion event
                sb.append(ControlCharacters.ESC).append("[<")
                    .append(cb).append(';').append(x).append(';')
                    .append(y).append('M')
            }
        } else if (isUrxvtMouseMode) {
            sb.append(ControlCharacters.ESC).append("[")
                .append(32 + cb).append(x).append(y).append('M')
        } else {
            charset = Charsets.ISO_8859_1
            sb.append(ControlCharacters.ESC).append("[M")
                .append((32 + cb).toChar()).append((32 + x).toChar()).append(x).append((32 + y).toChar())
        }

        ptyConnector.write(sb.toString().toByteArray(charset))

        if (log.isTraceEnabled) {
            log.trace("Send ESC{}", sb.substring(1))
        }
    }

}