package app.termora.terminal

import com.formdev.flatlaf.util.SystemInfo
import org.apache.commons.lang3.StringUtils
import java.awt.event.KeyEvent

@Suppress("MemberVisibilityCanBePrivate")
open class KeyEncoderImpl(private val terminal: Terminal) : KeyEncoder, DataListener {

    private val mapping = mutableMapOf<TerminalKeyEvent, String>()
    private val nothing = StringUtils.EMPTY

    init {

        val terminalModel = terminal.getTerminalModel()
        if (terminalModel.getData(DataKey.ApplicationCursorKeys, false)) {
            arrowKeysApplicationSequences()
        } else {
            arrowKeysAnsiCursorSequences()
        }


        if (terminalModel.getData(DataKey.AlternateKeypad, false)) {
            keypadApplicationSequences()
        } else {
            keypadAnsiSequences()
        }

        configureLeftRight()

        // Ctrl + C: 0x7F ASCII Delete
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_BACK_SPACE), String(byteArrayOf(0x7F)))

        // Enter
        if (terminalModel.getData(DataKey.AutoNewline, false)) {
            putCode(TerminalKeyEvent(keyCode = 10), encode = "\r\n")
        } else {
            putCode(TerminalKeyEvent(keyCode = 10), encode = "\r")
        }


        // Page Up
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_PAGE_UP), encode = "${ControlCharacters.ESC}[5~")
        // Page Down
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_PAGE_DOWN), encode = "${ControlCharacters.ESC}[6~")


        // Insert
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_INSERT), encode = "${ControlCharacters.ESC}[2~")
        // Delete
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_DELETE), encode = "${ControlCharacters.ESC}[3~")

        // Function Keys
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_F1), encode = "${ControlCharacters.ESC}OP")
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_F2), encode = "${ControlCharacters.ESC}OQ")
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_F3), encode = "${ControlCharacters.ESC}OR")
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_F4), encode = "${ControlCharacters.ESC}OS")
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_F5), encode = "${ControlCharacters.ESC}[15~")
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_F6), encode = "${ControlCharacters.ESC}[17~")
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_F7), encode = "${ControlCharacters.ESC}[18~")
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_F8), encode = "${ControlCharacters.ESC}[19~")
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_F9), encode = "${ControlCharacters.ESC}[20~")
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_F10), encode = "${ControlCharacters.ESC}[21~")
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_F11), encode = "${ControlCharacters.ESC}[23~")
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_F12), encode = "${ControlCharacters.ESC}[24~")

        terminal.getTerminalModel().addDataListener(object : DataListener {
            override fun onChanged(key: DataKey<*>, data: Any) {
                this@KeyEncoderImpl.onChanged(key, data)
            }
        })

    }

    override fun encode(event: TerminalKeyEvent): String {
        if (mapping.containsKey(event)) {
            return mapping.getValue(event)
        }

        var bytes = (mapping[TerminalKeyEvent(event.keyCode, 0)] ?: return nothing).toByteArray()

        if (alwaysSendEsc(event.keyCode) && (event.modifiers and TerminalEvent.ALT_MASK) != 0) {
            bytes = insertCodeAt(bytes, makeCode(ControlCharacters.ESC.code), 0)
            return String(bytes)
        }

        if (alwaysSendEsc(event.keyCode) && (event.modifiers and TerminalEvent.META_MASK) != 0) {
            bytes = insertCodeAt(bytes, makeCode(ControlCharacters.ESC.code), 0)
            return String(bytes)
        }

        if (isCursorKey(event.keyCode) || isFunctionKey(event.keyCode)) {
            bytes = getCodeWithModifiers(bytes, event.modifiers)
            return String(bytes)
        }

        return String(bytes)
    }

    private fun makeCode(vararg bytesAsInt: Int): ByteArray {
        val bytes = ByteArray(bytesAsInt.size)
        for ((i, byteAsInt) in bytesAsInt.withIndex()) {
            bytes[i] = byteAsInt.toByte()
        }
        return bytes
    }

    private fun alwaysSendEsc(key: Int): Boolean {
        return isCursorKey(key) || key == '\b'.code
    }

    override fun getTerminal(): Terminal {
        return terminal
    }

    internal fun putCode(event: TerminalKeyEvent, encode: String) {
        mapping[event] = encode
    }


    /**
     * Refer to section PC-Style Function Keys in http://invisible-island.net/xterm/ctlseqs/ctlseqs.html
     */
    private fun getCodeWithModifiers(bytes: ByteArray, modifiers: Int): ByteArray {
        val code = modifiersToCode(modifiers)

        if (code > 0 && bytes.size > 2) {
            // SS3 needs to become CSI.
            if (bytes[0].toInt() == ControlCharacters.ESC.code && bytes[1] == 'O'.code.toByte()) {
                bytes[1] = '['.code.toByte()
            }
            // If the control sequence has no parameters, it needs a default parameter.
            // Either way it also needs a semicolon separator.
            val prefix = if (bytes.size == 3) "1;" else ";"
            return insertCodeAt(
                bytes,
                (prefix + code).toByteArray(),
                bytes.size - 1
            )
        }

        return bytes
    }

    private fun insertCodeAt(bytes: ByteArray, code: ByteArray, at: Int): ByteArray {
        val res = ByteArray(bytes.size + code.size)
        System.arraycopy(bytes, 0, res, 0, bytes.size)
        System.arraycopy(bytes, at, res, at + code.size, bytes.size - at)
        System.arraycopy(code, 0, res, at, code.size)
        return res
    }

    /**
     *
     * Code     Modifiers
     * ------+--------------------------
     * 2     | Shift
     * 3     | Alt
     * 4     | Shift + Alt
     * 5     | Control
     * 6     | Shift + Control
     * 7     | Alt + Control
     * 8     | Shift + Alt + Control
     * 9     | Meta
     * 10    | Meta + Shift
     * 11    | Meta + Alt
     * 12    | Meta + Alt + Shift
     * 13    | Meta + Ctrl
     * 14    | Meta + Ctrl + Shift
     * 15    | Meta + Ctrl + Alt
     * 16    | Meta + Ctrl + Alt + Shift
     * ------+--------------------------
     * @param modifiers
     * @return
     */
    private fun modifiersToCode(modifiers: Int): Int {
        var code = 0
        if ((modifiers and TerminalEvent.SHIFT_MASK) != 0) {
            code = code or 1
        }
        if ((modifiers and TerminalEvent.ALT_MASK) != 0) {
            code = code or 2
        }
        if ((modifiers and TerminalEvent.CTRL_MASK) != 0) {
            code = code or 4
        }
        if ((modifiers and TerminalEvent.META_MASK) != 0) {
            code = code or 8
        }
        return if (code != 0) code + 1 else 0
    }

    private fun isCursorKey(key: Int): Boolean {
        return key == KeyEvent.VK_DOWN || key == KeyEvent.VK_UP
                || key == KeyEvent.VK_LEFT || key == KeyEvent.VK_RIGHT
                || key == KeyEvent.VK_HOME || key == KeyEvent.VK_END
    }

    private fun isFunctionKey(key: Int): Boolean {
        return key >= KeyEvent.VK_F1 && key <= KeyEvent.VK_F12
                || key == KeyEvent.VK_INSERT || key == KeyEvent.VK_DELETE
                || key == KeyEvent.VK_PAGE_UP || key == KeyEvent.VK_PAGE_DOWN
    }

    private fun arrowKeysApplicationSequences() {
        // Up
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_UP), encode = "${ControlCharacters.ESC}OA")
        // Down
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_DOWN), encode = "${ControlCharacters.ESC}OB")
        // Left
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_LEFT), encode = "${ControlCharacters.ESC}OD")
        // Right
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_RIGHT), encode = "${ControlCharacters.ESC}OC")
    }

    private fun arrowKeysAnsiCursorSequences() {
        // Up
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_UP), encode = "${ControlCharacters.ESC}[A")
        // Down
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_DOWN), encode = "${ControlCharacters.ESC}[B")
        // Left
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_LEFT), encode = "${ControlCharacters.ESC}[D")
        // Right
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_RIGHT), encode = "${ControlCharacters.ESC}[C")
    }

    /**
     * Alt + Left/Right
     */
    private fun configureLeftRight() {
        if (SystemInfo.isMacOS) {
            putCode(
                TerminalKeyEvent(keyCode = KeyEvent.VK_LEFT, TerminalEvent.ALT_MASK),
                encode = "${ControlCharacters.ESC}b"
            )
            putCode(
                TerminalKeyEvent(keyCode = KeyEvent.VK_RIGHT, TerminalEvent.ALT_MASK),
                encode = "${ControlCharacters.ESC}f"
            )
        } else {
            // ^[[1;5D
            putCode(
                TerminalKeyEvent(keyCode = KeyEvent.VK_LEFT, TerminalEvent.CTRL_MASK),
                "${ControlCharacters.ESC}[1;5D"
            )
            // ^[[1;5C
            putCode(
                TerminalKeyEvent(keyCode = KeyEvent.VK_RIGHT, TerminalEvent.CTRL_MASK),
                "${ControlCharacters.ESC}[1;5C"
            )
            // ^[[1;3D
            putCode(
                TerminalKeyEvent(keyCode = KeyEvent.VK_LEFT, TerminalEvent.ALT_MASK),
                "${ControlCharacters.ESC}[1;3D"
            )
            // ^[[1;3C
            putCode(
                TerminalKeyEvent(keyCode = KeyEvent.VK_RIGHT, TerminalEvent.ALT_MASK),
                "${ControlCharacters.ESC}[1;3C"
            )
        }
    }


    private fun keypadApplicationSequences() {
        // Up
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_KP_UP), encode = "${ControlCharacters.ESC}OA")
        // Down
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_KP_DOWN), encode = "${ControlCharacters.ESC}OB")
        // Left
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_KP_LEFT), encode = "${ControlCharacters.ESC}OD")
        // Right
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_KP_RIGHT), encode = "${ControlCharacters.ESC}OC")
        // Home
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_HOME), encode = "${ControlCharacters.ESC}OH")
        // End
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_END), encode = "${ControlCharacters.ESC}OF")
    }

    private fun keypadAnsiSequences() {
        // Up
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_KP_UP), encode = "${ControlCharacters.ESC}[A")
        // Down
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_KP_DOWN), encode = "${ControlCharacters.ESC}[B")
        // Left
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_KP_LEFT), encode = "${ControlCharacters.ESC}[D")
        // Right
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_KP_RIGHT), encode = "${ControlCharacters.ESC}[C")
        // Home
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_HOME), encode = "${ControlCharacters.ESC}[H")
        // End
        putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_END), encode = "${ControlCharacters.ESC}[F")
    }

    override fun onChanged(key: DataKey<*>, data: Any) {
        if (key == DataKey.ApplicationCursorKeys) {
            if (data as Boolean) {
                arrowKeysApplicationSequences()
            } else {
                arrowKeysAnsiCursorSequences()
            }
        } else if (key == DataKey.AlternateKeypad) {
            if (data as Boolean) {
                keypadApplicationSequences()
            } else {
                keypadAnsiSequences()
            }
        } else if (key == DataKey.AutoNewline) {
            if (data as Boolean) {
                putCode(TerminalKeyEvent(keyCode = 10), encode = "\r\n")
            } else {
                putCode(TerminalKeyEvent(keyCode = 10), encode = "\r")
            }
        }
    }


}