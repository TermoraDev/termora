package app.termora.cli

/** CLI 退出码约定 */
object ExitCodes {
    const val OK = 0
    const val USAGE = 1          // 参数错误
    const val NOT_FOUND = 2      // 主机不存在
    const val FORBIDDEN = 3      // 白名单未授权
    const val DANGEROUS = 4      // 危险命令被拦截
    const val CONNECT_FAIL = 5   // 连接/认证失败
    const val DISABLED = 6       // termora-cli 总开关未启用
}

/** 携带退出码的受控错误；CLI 顶层捕获后打印 message 到 stderr 并以该码退出 */
class CliError(val code: Int, message: String, cause: Throwable? = null) : RuntimeException(message, cause)
