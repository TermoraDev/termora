package app.termora

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils

/**
 * 用户需要保证自己的电脑是可信环境
 */
internal class LocalSecret private constructor() {

    companion object {
        fun getInstance(): LocalSecret {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(LocalSecret::class) { LocalSecret() }
        }
    }

    /**
     * 一个 16 长度的密码
     */
    val password: String = StringUtils.substring(DigestUtils.sha256Hex(SystemUtils.USER_NAME), 0, 16)

    /**
     * 一个 12 长度的盐
     */
    val salt: String = StringUtils.substring(password, 0, 16)

}