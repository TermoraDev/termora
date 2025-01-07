package app.termora.terminal

import org.apache.commons.codec.binary.Base64
import org.slf4j.LoggerFactory
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class OperatingSystemCommandProcessor(terminal: Terminal, reader: TerminalReader) :
    AbstractProcessor(terminal, reader) {
    private val args = StringBuilder()
    private val colorPalette get() = terminal.getTerminalModel().getColorPalette()

    companion object {
        private val log = LoggerFactory.getLogger(OperatingSystemCommandProcessor::class.java)
    }

    override fun process(ch: Char): ProcessorState {
        // 回退回去，然后重新读取出来
        reader.addFirst(ch)

        do {

            val c = reader.read()
            args.append(c)
            if (c == ControlCharacters.BEL || c == ControlCharacters.ST) {
                args.deleteAt(args.lastIndex)
                break
            } else if (c == '\\' && args.length >= 2 && args[args.length - 2] == ControlCharacters.ESC) {
                args.deleteAt(args.lastIndex)
                args.deleteAt(args.lastIndex)
                break
            }

            // 如果没有检测到结束，那么退出重新来
            if (reader.isEmpty()) {
                return TerminalState.OSC
            }

        } while (reader.isNotEmpty())


        // process osc
        processOperatingSystemCommandProcessor()

        args.clear()

        return TerminalState.READY
    }

    /**
     * Operating System Command (OSC  is 0x9d).
     * https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h3-Operating-System-Commands
     */
    private fun processOperatingSystemCommandProcessor() {
        val idx = args.indexOfFirst { it == ';' }
        if (idx == -1) {
            return
        }
        val prefix = args.substring(0, idx)
        val suffix = args.substring(prefix.length + 1)
        when (val mode = prefix.toIntOrNull() ?: -1) {
            // window title
            0,
            2 -> {
                terminal.getTerminalModel().setData(DataKey.WindowTitle, suffix)
                if (log.isDebugEnabled) {
                    log.debug("Window Title: $suffix")
                }
            }

            // icon title
            1 -> {
                terminal.getTerminalModel().setData(DataKey.IconTitle, suffix)
                if (log.isDebugEnabled) {
                    log.debug("Icon Title: $suffix")
                }
            }

            // workdir
            7 -> {
                terminal.getTerminalModel().setData(DataKey.Workdir, suffix)
                if (log.isDebugEnabled) {
                    log.debug("Workdir: $suffix")
                }
            }

            // hyperlink https://gist.github.com/egmontkob/eb114294efbcd5adb1944c9f3cb5feda
            8 -> {
                if (log.isDebugEnabled) {
                    log.debug("Ignore hyperlink OSC: 8")
                }
            }

            // 11: background color
            // 10: foreground color
            11, 10 -> {
                val terminalColor = if (mode == 10) TerminalColor.Normal.WHITE else TerminalColor.Normal.BLACK
                replyColor(mode, terminalColor)
            }

            // Ps = 5 2  ⇒  Manipulate Selection Data.  These controls may be disabled using the allowWindowOps resource.  The parameter Pt is parsed as
            52 -> {
                val pair = suffix.split(";", limit = 2).let {
                    Pair(it.first(), it.last())
                }

                // base64
                if (pair.first == "c") {
                    val text = String(Base64.decodeBase64(pair.second))
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
                    if (log.isDebugEnabled) {
                        log.debug("Copy {} to clipboard", text)
                    }
                } else if (log.isWarnEnabled) {
                    log.warn("Manipulate Selection Data. Unknown: {}", pair)
                }

            }

            else -> {
                if (log.isWarnEnabled) {
                    log.warn("Unknown OSC: $prefix")
                }
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun replyColor(mode: Int, terminalColor: TerminalColor) {
        val color = colorPalette.getColor(terminalColor)
        val red = (((color shr 16) and 0xFF) * 0x101).toHexString().substring(4)
        val green = (((color shr 8) and 0xFF) * 0x101).toHexString().substring(4)
        val blue = ((color and 0xFF) * 0x101).toHexString().substring(4)
        val buffer = "${ControlCharacters.ESC}]${mode};rgb:${red}/${green}/${blue}${ControlCharacters.BEL}"

        if (log.isDebugEnabled) {
            log.debug("OSC reply color: $buffer")
        }
    }


}
