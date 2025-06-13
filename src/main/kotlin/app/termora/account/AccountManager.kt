package app.termora.account

import app.termora.*
import app.termora.Application.ohMyJson
import app.termora.plugin.ExtensionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import java.security.PrivateKey
import java.security.PublicKey
import javax.swing.SwingUtilities

class AccountManager private constructor() : ApplicationRunnerExtension {
    companion object {
        fun getInstance(): AccountManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(AccountManager::class) { AccountManager() }
        }

        fun isLocally(id: String): Boolean {
            return StringUtils.isBlank(id) || id == "0"
        }
    }

    private var account = locally()
    private val accountProperties get() = AccountProperties.getInstance()

    fun getAccount() = account
    fun getAccountId() = account.id
    fun getServer() = account.server
    fun getEmail() = account.email
    fun getSubscriptions() = account.subscriptions
    fun getTeams() = account.teams
    fun getSecretKey() = account.secretKey
    fun getPublicKey() = account.publicKey
    fun getPrivateKey() = account.privateKey
    fun isSigned() = isFreePlan().not() && accountProperties.signed
    fun isLocally() = account.isLocally
    fun getLastSynchronizationOn() = accountProperties.lastSynchronizationOn
    fun getAccessToken() = account.accessToken
    fun getRefreshToken() = account.refreshToken
    fun getOwnerIds() = account.teams.map { it.id }.toMutableList().apply { add(getAccountId()) }.toSet()

    fun isFreePlan(): Boolean {
        return isLocally() || getSubscription().plan == SubscriptionPlan.Free
    }

    fun getSubscription(): Subscription {

        if (isLocally().not()) {
            val subscriptions = getSubscriptions()
            val enterprises = getSubscriptions().filter { it.plan == SubscriptionPlan.Enterprise }
            val teams = subscriptions.filter { it.plan == SubscriptionPlan.Team }
            val pros = subscriptions.filter { it.plan == SubscriptionPlan.Pro }
            val now = System.currentTimeMillis()

            if (enterprises.any { it.endAt > now }) {
                return enterprises.first { it.endAt > now }
            } else if (teams.any { it.endAt > now }) {
                return teams.first { it.endAt > now }
            } else if (pros.any { it.endAt > now }) {
                return pros.first { it.endAt > now }
            }
        }

        return Subscription(id = "0", plan = SubscriptionPlan.Free, startAt = 0, endAt = 0)
    }

    fun hasTeamFeature(): Boolean {
        if (accountProperties.signed.not()) return false
        val plan = getSubscription().plan
        return SubscriptionPlan.Team == plan || SubscriptionPlan.Enterprise == plan
    }


    /**
     * 刷新 Token
     */
    internal fun refreshToken() {
        val body = ohMyJson.encodeToString(mapOf("refreshToken" to getRefreshToken()))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("${getServer()}/v1/token")
            .header("Authorization", "Bearer ${getRefreshToken()}")
            .post(body)
            .build()
        val response = Application.httpClient.newCall(request).execute()
        if (response.code == 401) {
            IOUtils.closeQuietly(response)
            logout()
            throw ResponseException(response.code, response)
        } else if (response.isSuccessful.not()) {
            IOUtils.closeQuietly(response)
            throw ResponseException(response.code, response)
        }

        val text = response.use { response.body.use { it?.string() } }
        if (text == null) {
            throw ResponseException(response.code, "response body is empty", response)
        }

        val json = ohMyJson.decodeFromString<JsonObject>(text)
        val accessToken = json["accessToken"]?.jsonPrimitive?.content
        val refreshToken = json["refreshToken"]?.jsonPrimitive?.content
        if (accessToken == null || refreshToken == null) {
            throw ResponseException(response.code, "token is empty", response)
        }

        // 设置用户信息
        login(account.copy(accessToken = accessToken, refreshToken = refreshToken))

    }

    /**
     * 设置账户信息，可以多次调用，每次修改用户信息都要通过这个方法
     */
    internal fun login(account: Account) {

        val oldAccount = this.account

        this.account = account

        // 立即保存到数据库
        val accountProperties = AccountProperties.getInstance()
        accountProperties.id = account.id
        accountProperties.server = account.server
        accountProperties.email = account.email
        accountProperties.teams = ohMyJson.encodeToString(account.teams)
        accountProperties.subscriptions = ohMyJson.encodeToString(account.subscriptions)
        accountProperties.accessToken = account.accessToken
        accountProperties.refreshToken = account.refreshToken
        accountProperties.secretKey = ohMyJson.encodeToString(account.secretKey)

        // 如果变更账户了，那么同步时间从0开始
        if (oldAccount.id != account.id) {
            accountProperties.nextSynchronizationSince = 0
        }

        if (isLocally().not()) {
            accountProperties.publicKey = Base64.encodeBase64String(account.publicKey.encoded)
            accountProperties.privateKey = Base64.encodeBase64String(account.privateKey.encoded)
        } else {
            accountProperties.publicKey = StringUtils.EMPTY
            accountProperties.privateKey = StringUtils.EMPTY
        }

        // 通知变化
        notifyAccountChanged(oldAccount, account)
    }

    private fun notifyAccountChanged(oldAccount: Account, newAccount: Account) {
        if (SwingUtilities.isEventDispatchThread()) {
            for (extension in ExtensionManager.getInstance().getExtensions(AccountExtension::class.java)) {
                extension.onAccountChanged(oldAccount, newAccount)
            }
        } else {
            SwingUtilities.invokeLater { notifyAccountChanged(oldAccount, newAccount) }
        }
    }

    internal fun logout() {
        if (isLocally().not()) {
            // 登入本地用户
            login(locally())
        }
    }

    private fun locally(): Account {
        return Account(
            id = "0",
            server = "locally",
            email = "locally",
            teams = emptyList(),
            subscriptions = listOf(),
            secretKey = byteArrayOf(),
            accessToken = StringUtils.EMPTY,
            refreshToken = StringUtils.EMPTY,
            publicKey = object : PublicKey {
                override fun getAlgorithm(): String? {
                    TODO("Not yet implemented")
                }

                override fun getFormat(): String? {
                    TODO("Not yet implemented")
                }

                override fun getEncoded(): ByteArray? {
                    TODO("Not yet implemented")
                }

            },
            privateKey = object : PrivateKey {
                override fun getAlgorithm(): String? {
                    TODO("Not yet implemented")
                }

                override fun getFormat(): String? {
                    TODO("Not yet implemented")
                }

                override fun getEncoded(): ByteArray? {
                    TODO("Not yet implemented")
                }

            }
        )

    }

    override fun ready() {
        if (isLocally().not()) {
            swingCoroutineScope.launch(Dispatchers.IO) { refreshToken() }
        }
    }


    /**
     * 刷新用户
     */
    fun refresh(accessToken: String = getAccessToken()) {

    }

    class AccountApplicationRunnerExtension private constructor() : ApplicationRunnerExtension {
        companion object {
            val instance by lazy { AccountApplicationRunnerExtension() }
        }

        override fun ready() {
            val accountManager = getInstance()
            val accountProperties = AccountProperties.getInstance()

            // 如果都是本地用户，那么可以忽略
            val id = accountProperties.id
            if (id.isBlank() || id == accountManager.getAccountId()) return

            // 初始化本地账户
            accountManager.account = Account(
                id = accountProperties.id,
                server = accountProperties.server,
                email = accountProperties.email,
                accessToken = accountProperties.accessToken,
                refreshToken = accountProperties.refreshToken,
                teams = ohMyJson.decodeFromString(accountProperties.teams),
                subscriptions = ohMyJson.decodeFromString(accountProperties.subscriptions),
                secretKey = ohMyJson.decodeFromString(accountProperties.secretKey),
                publicKey = RSA.generatePublic(Base64.decodeBase64(accountProperties.publicKey)),
                privateKey = RSA.generatePrivate(Base64.decodeBase64(accountProperties.privateKey))
            )

        }

        override fun ordered(): Long {
            return 0
        }
    }


}