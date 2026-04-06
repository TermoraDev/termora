package app.termora.account

import app.termora.AES
import app.termora.Application.ohMyJson
import app.termora.ApplicationScope
import app.termora.PBKDF2
import app.termora.RSA
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.codec.binary.Base64
import java.util.concurrent.atomic.AtomicBoolean

class ServerManager private constructor() {
    companion object {
        fun getInstance(): ServerManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(ServerManager::class) { ServerManager() }
        }
    }


    private val isLoggingIn = AtomicBoolean(false)
    private val accountManager get() = AccountManager.getInstance()


    /**
     * 登录，不报错就是登录成功
     */
    fun login(server: Server, refreshToken: String, password: String) {

        if (accountManager.isLocally().not()) {
            throw IllegalStateException("Already logged in")
        }

        if (isLoggingIn.compareAndSet(false, true).not()) {
            throw IllegalStateException("Logging in")
        }

        try {
            doLogin(server, refreshToken, password)
        } finally {
            isLoggingIn.compareAndSet(true, false)
        }

    }

    private fun doLogin(server: Server, refreshToken: String, password: String) {
        // 服务器信息
        val serverInfo = getServerInfo(server)

        // call login
        val loginResponse = callToken(server, refreshToken)

        // call me
        val meResponse = callMe(server.server, loginResponse.accessToken)

        // 解密
        val salt = "${serverInfo.salt}:${meResponse.email}".toByteArray()
        val privateKeySecureKey = PBKDF2.hash(salt, password.toCharArray(), 1024, 256)
        val privateKeySecureIv = PBKDF2.hash(salt, password.toCharArray(), 1024, 128)
        val privateKeyEncoded = AES.CBC.decrypt(
            privateKeySecureKey, privateKeySecureIv,
            Base64.decodeBase64(meResponse.privateKey)
        )
        val privateKey = RSA.generatePrivate(privateKeyEncoded)
        val publicKey = RSA.generatePublic(Base64.decodeBase64(meResponse.publicKey))
        val secretKey = RSA.decrypt(privateKey, Base64.decodeBase64(meResponse.secretKey))

        val teams = mutableListOf<Team>()
        for (team in meResponse.teams) {
            teams.add(
                Team(
                    id = team.id,
                    name = team.name,
                    secretKey = RSA.decrypt(privateKey, Base64.decodeBase64(team.secretKey)),
                    role = team.role
                )
            )
        }

        // 登录成功
        accountManager.login(
            Account(
                id = meResponse.id,
                server = server.server,
                email = meResponse.email,
                teams = teams,
                subscriptions = meResponse.subscriptions,
                accessToken = loginResponse.accessToken,
                refreshToken = loginResponse.refreshToken,
                secretKey = secretKey,
                publicKey = publicKey,
                privateKey = privateKey,
            )
        )
    }

    private fun getServerInfo(server: Server): ServerInfo {
        val request = Request.Builder()
            .url("${server.server}/v1/client/system")
            .get()
            .build()

        return ohMyJson.decodeFromString<ServerInfo>(AccountHttp.execute(request = request))
    }

    private fun callToken(
        server: Server,
        refreshToken: String,
    ): LoginResponse {
        val body = ohMyJson.encodeToString(mapOf("refreshToken" to refreshToken))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("${server.server}/v1/token")
            .header("Authorization", "Bearer $refreshToken")
            .post(body)
            .build()

        val response = AccountHttp.client.newCall(request).execute()
        val text = response.use { response.body.use { it.string() } }

        if (response.isSuccessful.not()) {
            val message = ohMyJson.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.content
            throw IllegalStateException(message)
        }

        return ohMyJson.decodeFromString<LoginResponse>(text)
    }


    fun callMe(server: String, accessToken: String): MeResponse {
        val request = Request.Builder()
            .url("${server}/v1/users/me")
            .header("Authorization", "Bearer $accessToken")
            .build()
        val text = AccountHttp.execute(request = request)
        return ohMyJson.decodeFromString<MeResponse>(text)
    }

    @Serializable
    data class ServerInfo(val salt: String)

    @Serializable
    data class LoginResponse(val accessToken: String, val refreshToken: String)

    @Serializable
    data class MeResponse(
        val id: String,
        val email: String,
        val publicKey: String,
        val privateKey: String,
        val secretKey: String,
        val teams: List<MeTeam>,
        val subscriptions: List<Subscription>,
    )


    @Serializable
    data class MeTeam(val id: String, val name: String, val role: String, val secretKey: String)
}