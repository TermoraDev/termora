package app.termora.snippet

import app.termora.ApplicationScope
import app.termora.I18n
import app.termora.Icons
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.terminal.ControlCharacters
import app.termora.terminal.panel.TerminalWriter

class SnippetAction private constructor() : AnAction(I18n.getString("termora.snippet.title"), Icons.codeSpan) {
    companion object {
        fun getInstance(): SnippetAction {
            return ApplicationScope.forApplicationScope().getOrCreate(SnippetAction::class) { SnippetAction() }
        }

        const val SNIPPET = "SnippetAction"

        // \r \n \t \a \e \b
        private val SpecialChars = mutableMapOf(
            'r' to '\r',
            'n' to '\n',
            't' to '\t',
            'a' to ControlCharacters.BEL,
            'e' to ControlCharacters.ESC,
            'b' to ControlCharacters.BS
        )
    }

    override fun actionPerformed(evt: AnActionEvent) {
        SnippetDialog(evt.window).isVisible = true
    }


    fun runSnippet(snippet: Snippet, writer: TerminalWriter) {
        if (snippet.type != SnippetType.Snippet) return
        writer.write(TerminalWriter.WriteRequest.fromBytes(unescape(snippet.snippet).toByteArray(writer.getCharset())))
    }

    private fun unescape(text: String): String {
        val chars = text.toCharArray()
        val sb = StringBuilder()
        for (i in chars.indices) {
            val c = chars[i]

            // 不是特殊字符不处理
            if (SpecialChars.containsKey(c).not()) {
                sb.append(c)
                continue
            }

            // 特殊字符前面不是 `\` 不处理
            if (chars.getOrNull(i - 1) != '\\') {
                sb.append(c)
                continue
            }

            // 如果构成的字符串是：\\r 就会生成 \r 字符串，并非转译成：CR
            if (chars.getOrNull(i - 2) == '\\') {
                sb.deleteCharAt(sb.length - 1)
                sb.append(c)
                continue
            }

            // 命中条件之后，那么 sb 最后一个字符肯定是 \
            sb.deleteCharAt(sb.length - 1)
            sb.append(SpecialChars.getValue(c))
        }

        return sb.toString()
    }
}