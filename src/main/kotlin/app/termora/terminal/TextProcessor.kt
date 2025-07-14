package app.termora.terminal

internal class TextProcessor(private val terminal: Terminal, private val reader: TerminalReader) : Processor {
    companion object {
        val Written = DataKey(String::class)
    }

    private val sb = StringBuilder()
    private val terminals = setOf(
        ControlCharacters.ESC, ControlCharacters.BEL, ControlCharacters.CR, ControlCharacters.LF,
        ControlCharacters.FF, ControlCharacters.VT, ControlCharacters.BS, ControlCharacters.TAB
    )
    private val graphicCharacterSet get() = terminal.getTerminalModel().getData(DataKey.GraphicCharacterSet)
    private val insertMode get() = terminal.getTerminalModel().getData(DataKey.InsertMode, false)

    override fun process(ch: Char): ProcessorState {

        // ignore
        if (ch.isNull) {
            return TerminalState.READY
        }

        // Designate G~ Character Set,
        sb.append(graphicCharacterSet.map(ch))


        // 如果没有数据了， 或者下一个已经确定不是字符，那么处理
        if (reader.isEmpty() || terminals.contains(reader.peek()) || insertMode) {
            // 将文本写入文档
            process()
            return TerminalState.READY
        }

        return TerminalState.Text
    }

    private fun process() {
        val text = sb.toString()
        terminal.getDocument().write(text)
        terminal.getTerminalModel().setData(Written, text)
        sb.clear()
    }
}