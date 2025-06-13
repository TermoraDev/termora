package app.termora.account

import app.termora.ApplicationScope
import app.termora.database.DatabaseManager
import org.apache.commons.lang3.StringUtils

/**
 * 账号配置
 */
class AccountProperties private constructor(databaseManager: DatabaseManager) :
    DatabaseManager.IProperties(databaseManager, "Setting.Account") {

    companion object {
        fun getInstance(): AccountProperties {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(AccountProperties::class) { AccountProperties(DatabaseManager.getInstance()) }
        }
    }


    /**
     * id
     */
    var id by StringPropertyDelegate(StringUtils.EMPTY)

    /**
     * server
     */
    var server by StringPropertyDelegate(StringUtils.EMPTY)

    /**
     * email
     */
    var email by StringPropertyDelegate(StringUtils.EMPTY)

    /**
     * team
     */
    var teams by StringPropertyDelegate(StringUtils.EMPTY)

    /**
     * team
     */
    var subscriptions by StringPropertyDelegate(StringUtils.EMPTY)

    /**
     * 最后同步时间（本地时间）
     */
    var lastSynchronizationOn by LongPropertyDelegate(0)

    /**
     * 下次同步时间的开始时间
     */
    var nextSynchronizationSince by LongPropertyDelegate(0)

    /**
     * 用户密钥，array string
     */
    var secretKey by StringPropertyDelegate(StringUtils.EMPTY)

    /**
     * 用户公钥匙，base64
     */
    var publicKey by StringPropertyDelegate(StringUtils.EMPTY)

    /**
     * 用户私钥，base64
     */
    var privateKey by StringPropertyDelegate(StringUtils.EMPTY)

    var accessToken by StringPropertyDelegate(StringUtils.EMPTY)
    var refreshToken by StringPropertyDelegate(StringUtils.EMPTY)

    /**
     * 服务器 是否经过验证
     */
    var signed by BooleanPropertyDelegate(false)

}