package app.termora

/**
 * 用于解析命令行
 *
 * 以空格分隔参数,支持单引号和双引号来包裹参数
 *
 * 连续两个引号认定为普通字符
 *
 * 例如:
 * ```
 * CommandLineArgs.parse("echo hello world") // ["echo", "hello", "world"]
 * CommandLineArgs.parse("echo 'hello world'") // ["echo", "hello world"]
 * CommandLineArgs.parse("echo \"hello world\"") // ["echo", "hello world"]
 * ```
 */
sealed class CommandLineArgs {
    abstract val commandLine: List<String>
    abstract val commandLineString: String

    private class LazyOfString(override val commandLineString: String) : CommandLineArgs() {
        override val commandLine: List<String> by lazy {
            parse(commandLineString)
        }
    }

    private class LazyOfList(override val commandLine: List<String>) : CommandLineArgs() {
        override val commandLineString: String by lazy {
            toCommandString()
        }
    }

    companion object {
        fun of(command: String): CommandLineArgs {
            return LazyOfString(command)
        }

        fun of(command: List<String>): CommandLineArgs {
            return LazyOfList(command)
        }

        private fun parse(command: String): List<String> {
            val result = mutableListOf<String>()
            var current = StringBuilder()
            var i = 0

            // 当前是否在引号内
            var inSingleQuotes = false
            var inDoubleQuotes = false

            // 用于检测连续引号
            var prevChar: Char? = null

            while (i < command.length) {
                val char = command[i]

                when {
                    // 处理连续的引号
                    (char == '\'' && prevChar == '\'') || (char == '"' && prevChar == '"') -> {
                        current.append(char)
                        prevChar = null  // 重置prevChar,因为连续引号已经被处理
                    }

                    // 处理单引号
                    char == '\'' && !inDoubleQuotes -> {
                        if (inSingleQuotes) {
                            // 如果下一个字符也是单引号,等待下一次循环处理
                            if (i + 1 < command.length && command[i + 1] == '\'') {
                                prevChar = char
                            } else {
                                inSingleQuotes = false
                            }
                        } else {
                            prevChar = char
                            inSingleQuotes = true
                        }
                    }

                    // 处理双引号
                    char == '"' && !inSingleQuotes -> {
                        if (inDoubleQuotes) {
                            // 如果下一个字符也是双引号,等待下一次循环处理
                            if (i + 1 < command.length && command[i + 1] == '"') {
                                prevChar = char
                            } else {
                                inDoubleQuotes = false
                            }
                        } else {
                            prevChar = char
                            inDoubleQuotes = true
                        }
                    }

                    // 处理空格
                    char.isWhitespace() && !inSingleQuotes && !inDoubleQuotes -> {
                        if (current.isNotEmpty()) {
                            result.add(current.toString())
                            current.clear()
                        }
                    }

                    // 普通字符
                    else -> {
                        current.append(char)
                        prevChar = char
                    }
                }

                i++
            }

            // 添加最后一个参数
            if (current.isNotEmpty()) {
                result.add(current.toString())
            }

            return result
        }
    }

    protected fun toCommandString(): String {
        if (commandLine.isEmpty()) return ""

        val result = StringBuilder(commandLine.sumOf { it.length + 3 }) // 预估容量：字符串长度总和 + 引号和空格的额外空间

        commandLine.forEachIndexed { index, command ->
            if (index > 0) {
                result.append(' ')
            }

            // 计算单引号和双引号,空格的出现次数
            var singleQuotes = 0
            var doubleQuotes = 0
            var whitespace = 0
            for (char in command) {
                when {
                    char == '"' -> doubleQuotes++
                    char == '\'' -> singleQuotes++
                    char.isWhitespace() -> whitespace++
                }
            }

            if (singleQuotes == 0 && doubleQuotes == 0 && whitespace == 0) {
                result.append(command)
                return@forEachIndexed
            }

            // 选择更优的引号类型
            when {
                doubleQuotes == 0 -> result.appendWithQuotesEscape(command, '"')
                singleQuotes == 0 -> result.appendWithQuotesEscape(command, '\'')
                doubleQuotes <= singleQuotes -> result.appendWithQuotesEscape(command, '"')
                else -> result.appendWithQuotesEscape(command, '\'')
            }
        }

        return result.toString()
    }

    private fun StringBuilder.appendWithQuotesEscape(arg: String, quote: Char) {
        append(quote)
        var lastIndex = 0
        arg.forEachIndexed { index, char ->
            if (char == quote) {
                append(arg, lastIndex, index).append(quote).append(quote)
                lastIndex = index + 1
            }
        }
        append(arg, lastIndex, arg.length).append(quote)
    }

    override fun toString(): String {
        return commandLineString
    }
}
