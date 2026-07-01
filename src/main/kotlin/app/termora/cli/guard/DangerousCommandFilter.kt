package app.termora.cli.guard

/**
 * 危险命令拦截。命令字符串层面的保守匹配，目的是降低误操作风险，并非完整安全沙箱。
 * match 返回命中的规则名；未命中返回 null。
 */
class DangerousCommandFilter(
    private val rules: List<Rule> = DEFAULT_RULES,
) {
    data class Rule(val name: String, val regex: Regex)

    fun match(command: String): String? {
        val c = command.trim()
        return rules.firstOrNull { it.regex.containsMatchIn(c) }?.name
    }

    companion object {
        // RegexOption.IGNORE_CASE 统一忽略大小写
        private fun r(name: String, pattern: String) =
            Rule(name, Regex(pattern, RegexOption.IGNORE_CASE))

        val DEFAULT_RULES: List<Rule> = listOf(
            // rm -rf / 或 /* 或 ~（中间允许任意空白与 sudo 前缀）
            // 标志段用前瞻同时要求含 r 与 f，兼容 -rf/-fr/-Rf/-rfv 等任意顺序与附加标志
            r("rm-rf-root", """\brm\s+(-[a-zA-Z]*\s+)*-(?=[a-zA-Z]*r)(?=[a-zA-Z]*f)[a-zA-Z]+\s+(/|/\*|~)(\s|$)"""),
            r("mkfs", """\bmkfs(\.\w+)?\b"""),
            r("dd-to-device", """\bdd\b.*\bof=/dev/"""),
            r("redirect-to-device", """>\s*/dev/sd"""),
            // 关机/重启类关键字锚定到命令位置（行首、sudo 之后，或 ;/&/| 等命令分隔符之后），
            // 不把普通空白当作命令边界，避免 cat halt.txt / echo reboot now 之类子串误报
            r("shutdown", """(?:^|[;|&]\s*|\bsudo\s+)(shutdown|reboot|halt|poweroff|init\s+0)\b"""),
            r("fork-bomb", """:\(\)\s*\{.*:\|:.*&.*\}\s*;\s*:"""),
            r("chmod-root", """\bchmod\s+-R\s+777\s+/(\s|$)"""),
            r("chown-root", """\bchown\s+-R\s+\S+\s+/(\s|$)"""),
        )
    }
}
