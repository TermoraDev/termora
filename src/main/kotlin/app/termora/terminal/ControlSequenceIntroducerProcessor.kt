package app.termora.terminal

import org.slf4j.LoggerFactory
import kotlin.math.max
import kotlin.math.min


class ControlSequenceIntroducerProcessor(terminal: Terminal, reader: TerminalReader) :
    AbstractProcessor(terminal, reader) {
    private val terminalModel get() = terminal.getTerminalModel()
    private val document get() = terminal.getDocument()
    private val args = StringBuilder()

    companion object {
        private val log = LoggerFactory.getLogger(ControlSequenceIntroducerProcessor::class.java)
        private val styles = hashMapOf<TextStyle, TextStyle>()
    }

    // 非法字符，需要重排序
    private val illegalChars = mutableListOf<Char>()


    override fun process(ch: Char): ProcessorState {
        var state = TerminalState.READY

        // 如果将要处理 CSI 时有非法字符，那么重排序。后则的判断是下面 when 的判断
        if (needReread(ch)) {
            return state
        }

        when (ch) {
            // CSI Pm m  Character Attributes (SGR)
            'm' -> {
                processCharacterAttributes()
            }

            // CSI Ps A  Cursor Up Ps Times (default = 1) (CUU)..
            'A' -> {
                val count = max(args.toInt(1), 1)
                var position = terminal.getCursorModel().getPosition()
                val top = terminalModel.getScrollingRegion().top

                position = if (position.y - count < top) {
                    position.copy(y = top)
                } else {
                    position.copy(y = position.y - count)
                }

                if (log.isDebugEnabled) {
                    log.debug("Cursor Up $count Times (CUU)")
                }

                terminal.getCursorModel().move(row = position.y, col = position.x)
            }

            // CSI Ps D  Cursor Backward Ps Times (default = 1) (CUB).
            'D' -> {
                val count = max(1, args.toInt(1))
                val position = terminal.getCursorModel().getPosition()
                terminal.getCursorModel().move(row = position.y, col = max(position.x - count, 1))
                if (log.isDebugEnabled) {
                    log.debug("Cursor Backward $count Times (CUB).")
                }
            }

            //CSI Ps SP q Set cursor style (DECSCUSR), VT520.
            'q' -> {
                when (args.first()) {
                    '0', '1', '2' -> {
                        terminalModel.setData(DataKey.CursorStyle, CursorStyle.Block)
                    }

                    '3', '4' -> {
                        terminalModel.setData(DataKey.CursorStyle, CursorStyle.Underline)
                    }

                    '5', '6' -> {
                        terminalModel.setData(DataKey.CursorStyle, CursorStyle.Bar)
                    }
                }

                if (log.isDebugEnabled) {
                    log.debug("Set cursor style (DECSCUSR), VT520.")
                }
            }

            // CSI Ps B  Cursor Down Ps Times (default = 1) (CUD).
            'B' -> {
                val count = max(args.toInt(1), 1)
                var position = terminal.getCursorModel().getPosition()
                val bottom = terminalModel.getScrollingRegion().bottom

                position = if (position.y + count > bottom) {
                    position.copy(y = bottom)
                } else {
                    position.copy(y = position.y + count)
                }

                if (log.isDebugEnabled) {
                    log.debug("Cursor Down $count Times (CUD)")
                }

                terminal.getCursorModel().move(row = position.y, col = position.x)

            }

            // CSI Ps @  Insert Ps (Blank) Character(s) (default = 1) (ICH).
            '@' -> {
                val count = args.toInt(1)
                val position = terminal.getCursorModel().getPosition()
                val line = terminal.getDocument().getCurrentTerminalLineBuffer().getScreenLineAt(position.y - 1)
                line.insertChars(
                    position.x - 1,
                    terminalModel.getCols(),
                    CharBuffer(
                        chars = CharArray(count) { Char.Space },
                        style = terminalModel.getData(DataKey.TextStyle)
                    )
                )
                if (log.isDebugEnabled) {
                    log.debug("Insert Ps (Blank) Character(s) (ICH). count:$count")
                }
            }


            // CSI Ps F  Cursor Preceding Line Ps Times (default = 1) (CPL).
            'F' -> {
                val count = max(1, args.toInt(1))
                var position = terminal.getCursorModel().getPosition()
                val top = terminalModel.getScrollingRegion().top
                position = if (position.y - count < top) {
                    position.copy(y = top)
                } else {
                    position.copy(y = position.y - count)
                }

                terminal.getCursorModel().move(row = position.y, col = 1)

                if (log.isDebugEnabled) {
                    log.debug("Cursor Preceding Line $count Times (default = 1) (CPL).")
                }
            }

            // CSI Ps E  Cursor Next Line Ps Times (default = 1) (CNL).
            'E' -> {
                val count = max(1, args.toInt(1))
                var position = terminal.getCursorModel().getPosition()
                val bottom = terminalModel.getScrollingRegion().bottom

                position = if (position.y + count > bottom) {
                    position.copy(y = bottom)
                } else {
                    position.copy(y = position.y + count)
                }

                terminal.getCursorModel().move(row = position.y, col = 1)

                if (log.isDebugEnabled) {
                    log.debug("Cursor Next Line Ps Times $count Times (default = 1) (CNL).")
                }
            }

            // CSI Ps C  Cursor Forward Ps Times (default = 1) (CUF).
            'C' -> {
                val count = max(args.toInt(1), 1)
                val position = terminal.getCursorModel().getPosition()
                terminal.getCursorModel().move(
                    row = position.y, col = min(
                        position.x + count,
                        terminalModel.getCols()
                    )
                )
                if (log.isDebugEnabled) {
                    log.debug("Cursor Forward $count Times (default = 1) (CUF).")
                }
            }

            // TODO Device Status Report (DSR).
            'n' -> {
            }

            // ECSI Ps J  Erase in Display (ED), VT100.
            'J' -> {
                // default 0
                if (args.isBlank()) {
                    args.append('0')
                }
                args.controlSequences().forEach {
                    terminal.getDocument().eraseInDisplay(it)
                }
            }

            // Erase in Line (EL) (DECSEL)
            'K' -> {
                terminal.getDocument().eraseInLine(args.toInt(0))
            }

            // CSI Ps ; Ps H    Cursor Position [row;column] (default = [1,1]) (CUP).
            'f', 'H' -> {

                var rows = 1
                var cols = 1

                if (args.isNotBlank()) {
                    val position = args.controlSequences()
                    rows = position.getOrElse(0) { 1 }
                    cols = position.getOrElse(1) { 1 }
                }

                if (terminalModel.isOriginMode()) {
                    // 源模式下减1是因为，1 就是 top
                    rows += terminalModel.getScrollingRegion().top - 1
                }

                if (rows > terminalModel.getScrollingRegion().bottom) {
                    rows = terminalModel.getScrollingRegion().bottom
                }

                terminal.getCursorModel().move(row = rows, col = min(cols, terminalModel.getCols()))

                if (log.isDebugEnabled) {
                    log.debug("Cursor Position [$rows;$cols] (CUP)")
                }

            }

            // CSI Ps G  Cursor Character Absolute  [column] (default = [row,1]) (CHA).
            'G' -> {
                val column = min(args.toInt(1), terminalModel.getCols())
                val row = terminal.getCursorModel().getPosition().y
                terminal.getCursorModel().move(row, column)

                if (log.isDebugEnabled) {
                    log.debug("Cursor Character Absolute  [$column] (CHA)")
                }

            }

            // CSI Ps T  Scroll down Ps lines (default = 1) (SD), VT420.
            'T' -> {
                val count = args.toInt(1)
                // 反向滚动
                terminal.getDocument().scroll(TerminalMouseButton.ScrollUp, count)
                if (log.isDebugEnabled) {
                    log.debug("Scroll down Ps lines (default = 1) (SD): $count")
                }
            }

            // CSI Ps S  Scroll up Ps lines (default = 1) (SU), VT420, ECMA-48.
            'S' -> {
                val count = args.toInt(1)
                for (i in 0 until count) {
                    terminal.getDocument().scroll(TerminalMouseButton.ScrollDown)
                }
                if (log.isDebugEnabled) {
                    log.debug("Scroll up Ps lines (default = 1) (SD): $count")
                }
            }

            // CSI Ps P  Delete Ps Character(s) (default = 1) (DCH).
            'P' -> {
                val position = terminal.getCursorModel().getPosition()
                val count = args.toInt(1)
                val offset = position.x - 1
                val line = terminal.getDocument().getCurrentTerminalLineBuffer().getScreenLineAt(position.y - 1)

                if (count > 0) {
                    // 删除字符
                    line.deleteChars(
                        offset,
                        count,
                        Pair(Char.Null, terminalModel.getData(DataKey.TextStyle))
                    )
                }

                if (log.isDebugEnabled) {
                    log.debug("Line:${position.y} Delete Ps Character(s): $count")
                }
            }

            // CSI Ps d  Line Position Absolute  [row] (default = [1,column]) (VPA).
            'd' -> {
                val position = terminal.getCursorModel().getPosition()
                val row = args.toInt(1)
                terminal.getCursorModel().move(row = row, col = position.x)
                if (log.isDebugEnabled) {
                    log.debug("Line Position Absolute  [$row]")
                }
            }

            // Set Mode (SM).
            'h' -> {
                enableMode()
            }


            // Reset Mode (RM).
            'l' -> {
                enableMode(false)
            }

            // CSI Ps X  Erase Ps Character(s) (default = 1) (ECH).
            'X' -> {
                val count = args.toInt(1)
                val position = terminal.getCursorModel().getPosition()
                val offset = position.x - 1
                val line = terminal.getDocument().getCurrentTerminalLineBuffer().getScreenLineAt(position.y - 1)
                line.eraseChars(offset, count, terminalModel.getData(DataKey.TextStyle))
            }

            // CSI Ps L  Insert Ps Line(s) (default = 1) (IL).
            'L' -> {
                val count = args.toInt(1)
                val position = terminal.getCursorModel().getPosition()
                // 添加行
                terminal.getDocument().getCurrentTerminalLineBuffer()
                    .insertLines(position.y, terminalModel.getScrollingRegion().bottom, count)
                if (log.isDebugEnabled) {
                    log.debug("Insert Ps Line(s) $count (IL).")
                }
            }

            // CSI Ps ; Ps r   Set Scrolling Region [top;bottom] (default = full size of window) (DECSTBM)
            'r' -> {
                if (args.startsWithQuestionMark()) {
                    return TerminalState.READY
                }

                val sr = args.controlSequences()
                var top = sr.getOrElse(0) { 1 }
                var bottom = sr.getOrElse(1) { terminalModel.getRows() }

                if (bottom <= top || top < 1) {
                    if (log.isWarnEnabled) {
                        log.warn("Set Scrolling Region Error. top: $top , bottom: $bottom")
                    }

                    top = 1
                    bottom = terminalModel.getRows()
                }

                // 设置滚动区域
                terminal.getTerminalModel().setData(DataKey.ScrollingRegion, ScrollingRegion(top, bottom))

                if (log.isDebugEnabled) {
                    log.debug("Set Scrolling Region [${top}; ${bottom}]")
                }

                // 光标移动到1:1，会相对于 Scrolling Region
                terminal.getCursorModel().move(row = if (terminalModel.isOriginMode()) top else 1, col = 1)

            }

            // todo Window manipulation (XTWINOPS)
            't' -> {
                if (log.isDebugEnabled) {
                    log.debug("Window manipulation (XTWINOPS) Ignore")
                }
            }

            // TODO Send Device Attributes (Primary DA).
            'c' -> {
            }

            // CSI Ps M  Delete Ps Line(s) (default = 1) (DL).
            'M' -> {
                val count = args.toInt(1)
                terminal.getDocument().getCurrentTerminalLineBuffer()
                    .deleteLines(
                        terminal.getCursorModel().getPosition().y,
                        terminalModel.getScrollingRegion().bottom,
                        count
                    )
                if (log.isDebugEnabled) {
                    log.debug("Delete $count Line(s) (DL).")
                }
            }

            // Tab Clear (TBC)
            // Ps = 0  ⇒  Clear Current Column (default).
            // Ps = 3  ⇒  Clear All.
            'g' -> {
                val mode = args.toInt(0)
                if (mode == 0) {
                    val x = terminal.getCursorModel().getPosition().x
                    terminal.getTabulator().clearTabStop(x)
                    if (log.isDebugEnabled) {
                        log.debug("Tab Clear (TBC). clearTabStop($x)")
                    }
                } else if (mode == 3) {
                    terminal.getTabulator().clearAllTabStops()
                    if (log.isDebugEnabled) {
                        log.debug("Tab Clear (TBC). clearAllTabStops")
                    }
                } else if (log.isWarnEnabled) {
                    log.warn("Tab Clear (TBC). TBC: $mode")
                }
            }

            // split
            ';' -> {
                args.append(ch)
                state = TerminalState.CSI
            }

            // illegal chars
            ControlCharacters.BS,
            ControlCharacters.TAB,
            ControlCharacters.FF,
            ControlCharacters.CR,
            ControlCharacters.LF,
            ControlCharacters.VT,
            ControlCharacters.ST,
                -> {
                illegalChars.add(ch)
                state = TerminalState.CSI
            }

            else -> {
                // 参数继续追加
                args.append(ch)
                // 如果是没有处理过的英文字符，那么警告一次
                if (ch in '@'..'~') {
                    if (log.isWarnEnabled) {
                        log.warn("Unknown CSI: $args")
                    }
                } else {
                    state = TerminalState.CSI
                }
            }
        }

        if (state == TerminalState.READY) {
            args.clear()
            illegalChars.clear()
        }

        return state

    }

    private fun needReread(ch: Char): Boolean {
        if (illegalChars.isNotEmpty() && ch.code in 0x40..0x7E) {

            val chars = mutableListOf<Char>()

            chars.addAll(illegalChars)
            chars.add(ControlCharacters.ESC)
            chars.add('[')
            chars.addAll(args.map { it })
            chars.add(ch)

            if (log.isDebugEnabled) {
                log.debug("Re-read: $chars")
            }
            reader.addFirst(chars)

            args.clear()
            illegalChars.clear()

            return true
        }

        return false
    }

    /**
     * https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h4-Functions-using-CSI-_-ordered-by-the-final-character-lparen-s-rparen:CSI-?-Pm-h.1D0E
     */
    private fun enableMode(enable: Boolean = true) {
        if (args.startsWithQuestionMark()) {
            enablePrivateMode(enable)
            return
        }

        when (args.toString().toInt()) {

            // Insert Mode (IRM).
            4 -> {
                terminalModel.setData(DataKey.InsertMode, enable)
                if (log.isDebugEnabled) {
                    log.debug("Insert Mode (IRM). $enable")
                }
            }

            // Automatic Newline (LNM).
            20 -> {
                terminalModel.setData(DataKey.AutoNewline, enable)
                if (log.isDebugEnabled) {
                    log.debug("Automatic Newline (LNM). $enable")
                }
            }

            else -> {
                if (log.isWarnEnabled) {
                    log.warn("Unknown Mode: $args")
                }
            }
        }
    }


    private fun enablePrivateMode(enable: Boolean = true) {
        args.controlSequences().forEach {
            when (it) {

                // Application Cursor Keys (DECCKM)
                1 -> {
                    terminalModel.setData(DataKey.ApplicationCursorKeys, enable)
                    if (log.isDebugEnabled) {
                        log.debug("Application Cursor Keys (DECCKM) $enable")
                    }
                }
                // Designate VT52 mode (DECANM), VT100.
                2 -> {
                    terminalModel.setData(DataKey.VT52Mode, enable)
                    if (log.isDebugEnabled) {
                        log.debug("VT52 mode (DECANM), VT100. $enable")
                    }
                }

                // Column Mode (DECCOLM)
                3 -> {
                    terminalModel.setData(DataKey.ColumnMode, enable)
                    // clear screen
                    document.eraseInDisplay(2)
                    // 设置窗口大小
                    terminalModel.setData(
                        DataKey.ScrollingRegion,
                        ScrollingRegion(top = 1, bottom = terminalModel.getRows())
                    )
                    // move caret
                    terminal.getCursorModel().move(1, 1)
                    if (log.isDebugEnabled) {
                        log.debug("Column Mode (DECCOLM) $enable")
                    }
                }

                // Smooth (Slow) Scroll (DECSCLM)
                4 -> {
                    terminalModel.setData(DataKey.SmoothScroll, enable)
                    if (log.isDebugEnabled) {
                        log.debug("Smooth (Slow) Scroll (DECSCLM) $enable")
                    }
                }

                // Reverse Video (DECSCNM)
                5 -> {
                    terminalModel.setData(DataKey.ReverseVideo, enable)
                    if (log.isDebugEnabled) {
                        log.debug("Reverse Video (DECSCNM) (DECOM) $enable")
                    }
                }

                // Origin Mode (DECOM)
                6 -> {
                    terminalModel.setData(DataKey.OriginMode, enable)
                    if (log.isDebugEnabled) {
                        log.debug("Origin Mode (DECOM) $enable")
                    }
                }

                // Auto-Wrap Mode (DECAWM)
                7 -> {
                    terminalModel.setData(DataKey.AutoWrapMode, enable)
                    if (log.isDebugEnabled) {
                        log.debug("Auto-Wrap Mode (DECAWM) $enable")
                    }
                }

                // Auto-Repeat Keys (DECARM)
                8 -> {
                    terminalModel.setData(DataKey.AutoRepeatKeys, enable)
                    if (log.isDebugEnabled) {
                        log.debug("Auto-Repeat Keys (DECARM) $enable")
                    }
                }

                // Hide cursor (DECTCEM), VT220.
                25 -> {
                    terminalModel.setData(DataKey.ShowCursor, enable)
                    if (log.isDebugEnabled) {
                        log.debug("Blinking cursor $enable")
                    }
                }

                // Allow 80 ⇒  132 mode, xterm.
                40 -> {
                    terminalModel.setData(DataKey.Allow80_132, enable)
                    if (log.isDebugEnabled) {
                        log.debug("Allow 80 ⇒  132 mode $enable")
                    }
                }

                // Reverse-wraparound mode
                45 -> {
                    terminalModel.setData(DataKey.ReverseWraparoundMode, enable)
                    if (log.isDebugEnabled) {
                        log.debug("Reverse-wraparound mode $enable")
                    }
                }


                // Ps = 1 0 0 0  ⇒  Send Mouse X & Y on button press and release.
                1000 -> {
                    if (enable) {
                        terminalModel.setData(DataKey.MouseMode, MouseMode.MOUSE_REPORTING_NORMAL)
                    } else {
                        terminalModel.setData(DataKey.MouseMode, MouseMode.MOUSE_REPORTING_NONE)
                    }
                    if (log.isDebugEnabled) {
                        log.debug("Send Mouse X & Y on button press and release. $enable")
                    }
                }

                // Ps = 1 0 0 1  ⇒  Use Hilite Mouse Tracking, xterm.
                1001 -> {
                    if (enable) {
                        terminalModel.setData(DataKey.MouseMode, MouseMode.MOUSE_REPORTING_HILITE)
                    } else {
                        terminalModel.setData(DataKey.MouseMode, MouseMode.MOUSE_REPORTING_NONE)
                    }
                    if (log.isDebugEnabled) {
                        log.debug("Use Hilite Mouse Tracking, xterm. $enable")
                    }
                }

                // Enable SGR Mouse Mode
                1006 -> {
                    terminalModel.setData(DataKey.SGRMouseMode, enable)
                    if (log.isDebugEnabled) {
                        log.debug("SGR Mouse Mode $enable")
                    }
                }

                // Ps = 1 0 1 5  ⇒  Enable urxvt Mouse Mode.
                1015 -> {
                    terminalModel.setData(DataKey.urxvtMouseMode, enable)
                    if (log.isDebugEnabled) {
                        log.debug("Enable urxvt Mouse Mode. $enable")
                    }
                }

                // Send FocusIn/FocusOut events, xterm.
                1004 -> {
                    terminalModel.setData(DataKey.SendFocusInFocusOutEvents, enable)
                    if (log.isDebugEnabled) {
                        log.debug("Send FocusIn/FocusOut events, xterm. $enable")
                    }
                }

                // https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h4-Functions-using-CSI-_-ordered-by-the-final-character-lparen-s-rparen:CSI-?-Pm-h:Ps-=-1-0-3-4.1F7F
                1034 -> {
                    terminalModel.setData(DataKey.EightBitInput, enable)
                    if (log.isDebugEnabled) {
                        log.debug("Eight Bit Input $enable")
                    }
                }

                // Alternate Screen Buffer
                // https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h2-The-Alternate-Screen-Buffer
                1049 -> {

                    // 如果是关闭 清屏
                    if (!enable) {
                        terminal.getDocument().eraseInDisplay(2)
                    }

                    // clear selection
                    terminal.getSelectionModel().clearSelection()

                    terminalModel.setData(DataKey.AlternateScreenBuffer, enable)

                    // 如果是开启全屏，滚动条重置
                    if (enable) {
                        // scroll reset
                        terminal.getScrollingModel().scrollTo(0)
                    }

                    if (log.isDebugEnabled) {
                        log.debug("Alternate Screen Buffer $enable")
                    }
                }

                // Set bracketed paste mode, xterm.
                2004 -> {
                    terminalModel.setData(DataKey.BracketedPasteMode, enable)
                    if (log.isDebugEnabled) {
                        log.debug("Set bracketed paste mode $enable")
                    }
                }

                // Ps = 1 2  ⇒  Start blinking cursor (AT&T 610).
                12 -> {
                    terminalModel.setData(DataKey.StartBlinkingCursor, enable)
                    if (log.isDebugEnabled) {
                        log.debug("Start blinking cursor (AT&T 610). $enable")
                    }
                }

                // Ps = 1 0 0 2  ⇒  Use Cell Motion Mouse Tracking, xterm.
                1002 -> {
                    if (enable) {
                        terminalModel.setData(DataKey.MouseMode, MouseMode.MOUSE_REPORTING_BUTTON_MOTION)
                    } else {
                        terminalModel.setData(DataKey.MouseMode, MouseMode.MOUSE_REPORTING_NONE)
                    }
                    if (log.isDebugEnabled) {
                        log.debug("Use Cell Motion Mouse Tracking, xterm. $enable")
                    }
                }
                // Ps = 1 0 0 3  ⇒  Use All Motion Mouse Tracking, xterm.
                1003 -> {
                    if (enable) {
                        terminalModel.setData(DataKey.MouseMode, MouseMode.MOUSE_REPORTING_ALL_MOTION)
                    } else {
                        terminalModel.setData(DataKey.MouseMode, MouseMode.MOUSE_REPORTING_NONE)
                    }
                    if (log.isDebugEnabled) {
                        log.debug("Use All Motion Mouse Tracking, xterm. $enable")
                    }
                }

                // 1 0 0 5  ⇒  Enable UTF-8 Mouse Mode, xterm.
                1005 -> {
                    terminalModel.setData(DataKey.UTF8MouseMode, enable)
                    if (log.isDebugEnabled) {
                        log.debug("Enable UTF-8 Mouse Mode, xterm. $enable")
                    }
                }


                // https://github.com/microsoft/terminal/blob/main/doc/specs/%234999%20-%20Improved%20keyboard%20handling%20in%20Conpty.md#requesting-win32-input-mode
                // win32-input-mode
                9001 -> {
                    if (log.isDebugEnabled) {
                        log.debug("win32-input-mode $enable")
                    }
                }

                else -> {
                    if (log.isWarnEnabled) {
                        log.warn("Unknown Private Mode: $args , enable: $enable")
                    }
                }
            }
        }
    }

    /**
     * CSI Pm m  Character Attributes (SGR).
     */
    private fun processCharacterAttributes() {
        val textStyle = terminal.getTerminalModel().getData(DataKey.TextStyle)
        var foreground = textStyle.foreground
        var background = textStyle.background
        var bold = textStyle.bold
        var dim = textStyle.dim
        var italic = textStyle.italic
        var underline = textStyle.underline
        var inverse = textStyle.inverse
        var lineThrough = textStyle.lineThrough
        var blink = textStyle.blink
        var doublyUnderline = textStyle.doublyUnderline

        // default 0
        if (args.isBlank()) {
            args.clear()
            args.append("0")
        } else if (args.startsWithMoreMark()) {
            return
        }

        val iterator = args.controlSequences().iterator()
        while (iterator.hasNext()) {
            when (val mode = iterator.nextInt()) {
                0 -> {
                    foreground = 0
                    background = 0
                    underline = false
                    bold = false
                    inverse = false
                    italic = false
                    doublyUnderline = false
                    blink = false
                    lineThrough = false
                    dim = false
                }

                // Bold
                1 -> bold = true
                // DIM
                2 -> dim = true
                //  italicized
                3 -> italic = true
                // Underlined
                4 -> underline = true
                // Blink
                5 -> blink = true
                // Inverse
                7 -> inverse = true
                // Crossed-out characters
                9 -> lineThrough = true
                // not Crossed-out characters
                29 -> lineThrough = false
                // Doubly-underlined
                21 -> doublyUnderline = true

                // Normal (neither bold nor faint)
                22 -> {
                    bold = false
                    dim = false
                }
                // not Blink
                25 -> blink = false
                // Inverse
                27 -> inverse = false
                // Not underlined
                24 -> underline = false
                // Not italicized
                23 -> italic = false

                // foreground
                30,
                31,
                32,
                33,
                34,
                35,
                36,
                37 -> foreground = mode - 30 + 1

                //  xterm-256 foreground color
                38 -> {
                    if (iterator.hasNext()) {
                        when (val code = iterator.next()) {
                            // rgb
                            2 -> {
                                val r = iterator.next()
                                val g = iterator.next()
                                val b = iterator.next()
                                foreground = 65536 * r + 256 * g + b
                            }

                            // index color
                            5 -> {
                                foreground = terminalModel.getColorPalette().getXTerm256Color(iterator.next() + 1)
                            }

                            else -> {
                                if (log.isWarnEnabled) {
                                    log.warn("xterm-256 foreground color, code: $code")
                                }
                            }
                        }
                    }
                    break
                }

                // foreground default
                39 -> foreground = 0

                // background black
                40,
                41,
                42,
                43,
                44,
                45,
                46,
                47 -> background = mode - 40 + 1

                //  xterm-256 background color
                48 -> {
                    if (iterator.hasNext()) {
                        when (val code = iterator.next()) {
                            // rgb
                            2 -> {
                                val r = iterator.next()
                                val g = iterator.next()
                                val b = iterator.next()
                                background = 65536 * r + 256 * g + b
                            }

                            // index color
                            5 -> {
                                background = terminalModel.getColorPalette().getXTerm256Color(iterator.next() + 1)
                            }

                            else -> {
                                if (log.isWarnEnabled) {
                                    log.warn("xterm-256 foreground color, code: $code")
                                }
                            }
                        }
                    }
                    break
                }

                // background default
                49 -> background = 0

                // iso colors
                // foreground white
                90,
                91,
                92,
                93,
                94,
                95,
                96,
                97 -> foreground = mode - 82 + 1


                // background black
                100,
                101,
                102,
                103,
                104,
                105,
                106,
                107 -> background = mode - 92 + 1

                else -> {
                    if (log.isWarnEnabled) {
                        log.warn("Unknown SGR: $args")
                    }
                }
            }
        }

        val style = TextStyle.Default.copy(
            foreground = foreground,
            background = background,
            underline = underline,
            italic = italic,
            bold = bold,
            inverse = inverse,
            lineThrough = lineThrough,
            blink = blink,
            dim = dim,
            doublyUnderline = doublyUnderline,
        )

        terminalModel.setData(DataKey.TextStyle, style)

        if (log.isDebugEnabled) {
            log.debug("SGR: $args")
        }
    }


    private fun StringBuilder.startsWithQuestionMark(): Boolean {
        return this.startsWith('?')
    }

    private fun StringBuilder.startsWithMoreMark(): Boolean {
        return this.startsWith('>')
    }

    private fun StringBuilder.toInt(defaultValue: Int): Int {
        if (isBlank()) {
            return defaultValue
        }
        return toString().toIntOrNull() ?: return defaultValue
    }

    private fun StringBuilder.controlSequences(): IntArray {
        val str = if (startsWithQuestionMark() || startsWithMoreMark()) substring(1) else this
        return str.split(";").filter { it.isNotBlank() }.mapNotNull {
            val t = it.toIntOrNull()
            if (t == null) {
                if (log.isErrorEnabled) {
                    log.error("$it is not a valid integer. args: $this")
                }
            }
            return@mapNotNull t
        }.toIntArray()
    }

}