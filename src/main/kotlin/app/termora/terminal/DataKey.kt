package app.termora.terminal

import app.termora.plugin.internal.AltKeyModifier
import kotlin.reflect.KClass


/**
 * https://invisible-island.net/xterm/ctlseqs/ctlseqs.html
 */
class DataKey<T : Any>(val clazz: KClass<T>) {
    companion object {

        /**
         * Designate G0 Character Set
         * Designate G1 Character Set
         * Designate G2 Character Set
         * Designate G3 Character Set
         */
        val GraphicCharacterSet = DataKey(app.termora.terminal.GraphicCharacterSet::class)

        /**
         * Save Cursor (DECSC), VT100.
         *
         * https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h4-Controls-beginning-with-ESC:ESC-7.C65
         */
        val SaveCursor = DataKey(CursorStore::class)

        /**
         * Current Text Style
         */
        val TextStyle = DataKey(app.termora.terminal.TextStyle::class)

        /**
         * Application Cursor Keys (DECCKM)
         */
        val ApplicationCursorKeys = DataKey(Boolean::class)


        /**
         * Designate VT52 mode (DECANM), VT100.
         *
         * https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h3-VT52-Mode
         */
        val VT52Mode = DataKey(Boolean::class)

        /**
         * Column Mode (DECCOLM)
         */
        val ColumnMode = DataKey(Boolean::class)

        /**
         * Set bracketed paste mode, xterm.
         *
         * https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h2-Bracketed-Paste-Mode
         */
        val BracketedPasteMode = DataKey(Boolean::class)

        /**
         * Ps = 1 2  ⇒  Start blinking cursor (AT&T 610).
         */
        val StartBlinkingCursor = DataKey(Boolean::class)

        /**
         * OSC Window Title
         */
        val WindowTitle = DataKey(String::class)

        /**
         * OSC Icon Title
         */
        val IconTitle = DataKey(String::class)

        /**
         * OSC Workdir
         */
        val Workdir = DataKey(String::class)

        /**
         * OSC 1337 CurrentDir
         *
         * https://iterm2.com/documentation-escape-codes.html
         */
        val CurrentDir = DataKey(String::class)

        /**
         * true: alternate keypad.
         * false: Normal Keypad (DECKPNM)
         */
        val AlternateKeypad = DataKey(Boolean::class)

        /**
         * Auto-Wrap Mode (DECAWM)
         */
        val AutoWrapMode = DataKey(Boolean::class)

        /**
         *  Ps = 2 5  ⇒  Show cursor (DECTCEM), VT220.
         *
         *  是否显示光标，默认 true
         */
        val ShowCursor = DataKey(Boolean::class)

        /**
         * Allow 80 ⇒  132 mode, xterm.
         */
        val Allow80_132 = DataKey(Boolean::class)

        /**
         * Reverse-wraparound mode (XTREVWRAP), xterm.
         */
        val ReverseWraparoundMode = DataKey(Boolean::class)

        /**
         * Origin Mode (DECOM)
         */
        val OriginMode = DataKey(Boolean::class)

        /**
         * Reverse Video (DECSCNM)
         */
        val ReverseVideo = DataKey(Boolean::class)

        /**
         * Auto-Repeat Keys (DECARM)
         */
        val AutoRepeatKeys = DataKey(Boolean::class)

        /**
         *  1 0 4 9
         *  https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h2-The-Alternate-Screen-Buffer
         */
        val AlternateScreenBuffer = DataKey(Boolean::class)

        /**
         *  1 0 3 4
         *  https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h4-Functions-using-CSI-_-ordered-by-the-final-character-lparen-s-rparen:CSI-?-Pm-h:Ps-=-1-0-3-4.1F7F
         */
        val EightBitInput = DataKey(Boolean::class)

        /**
         * Set Scrolling Region [top;bottom] (default = full size of window) (DECSTBM)
         */
        val ScrollingRegion = DataKey(app.termora.terminal.ScrollingRegion::class)

        /**
         * Smooth (Slow) Scroll (DECSCLM)
         */
        val SmoothScroll = DataKey(Boolean::class)

        /**
         * Insert Mode (IRM).
         */
        val InsertMode = DataKey(Boolean::class)

        /**
         * Automatic Newline (LNM).
         */
        val AutoNewline = DataKey(Boolean::class)

        /**
         * SGR Mouse Mode
         */
        val SGRMouseMode = DataKey(Boolean::class)

        /**
         * Enable UTF-8 Mouse Mode
         */
        val UTF8MouseMode = DataKey(Boolean::class)

        /**
         * Send FocusIn/FocusOut events, xterm.
         *
         * https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h4-Functions-using-CSI-_-ordered-by-the-final-character-lparen-s-rparen:CSI-?-Pm-h:Ps-=-1-0-0-4.1F7C
         */
        val SendFocusInFocusOutEvents = DataKey(Boolean::class)

        /**
         * Mouse mode
         */
        val MouseMode = DataKey(app.termora.terminal.MouseMode::class)

        /**
         * Ps = 1 0 1 5  ⇒  Enable urxvt Mouse Mode.
         */
        val urxvtMouseMode = DataKey(Boolean::class)

        /**
         * CSI Ps SP q Set cursor style (DECSCUSR), VT520.
         */
        val CursorStyle = DataKey(app.termora.terminal.CursorStyle::class)

        /**
         * TerminalWriter
         */
        val TerminalWriter = DataKey(app.termora.terminal.panel.TerminalWriter::class)

        /**
         * [app.termora.plugin.internal.AltKeyModifier]
         */
        val AltModifier = DataKey(AltKeyModifier::class)
    }
}


