package app.termora.account

import app.termora.*
import app.termora.Application.ohMyJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
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
    fun login(server: Server, username: String, password: String) {

        if (accountManager.isLocally().not()) {
            throw IllegalStateException("Already logged in")
        }

        if (isLoggingIn.compareAndSet(false, true).not()) {
            throw IllegalStateException("Logging in")
        }

        try {
            doLogin(server, username, password)
        } finally {
            isLoggingIn.compareAndSet(true, false)
        }

    }

    private fun doLogin(server: Server, username: String, password: String) {
        // 服务器信息
        val serverInfo = getServerInfo(server)

        // call login
        val loginResponse = callLogin(serverInfo, server, username, password)

        // call me
        val meResponse = callMe(server, loginResponse.accessToken)

        // 解密
        val salt = "${serverInfo.salt}:${username}".toByteArray()
        val privateKeySecureKey = PBKDF2.hash(salt, username.toCharArray(), 1024, 256)
        val privateKeySecureIv = PBKDF2.hash(salt, username.toCharArray(), 1024, 128)
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

    private fun callLogin(serverInfo: ServerInfo, server: Server, username: String, password: String): LoginResponse {

        val passwordHex = DigestUtils.sha256Hex("${serverInfo.salt}:${username}:${password}")
        val requestBody = ohMyJson.encodeToString(mapOf("email" to username, "password" to passwordHex))
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${server.server}/v1/login")
            .post(requestBody)
            .build()

        val response = AccountHttp.client.newCall(request).execute()
        val text = response.use { response.body.use { it?.string() } }

        if (text == null) {
            throw ResponseException(response.code, response)
        }

        if (response.isSuccessful.not()) {
            val message = ohMyJson.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.content
            throw IllegalStateException(message)
        }

        return ohMyJson.decodeFromString<LoginResponse>(text)
    }


    private fun callMe(server: Server, accessToken: String): MeResponse {
        val request = Request.Builder()
            .url("${server.server}/v1/users/me")
            .header("Authorization", "Bearer $accessToken")
            .build()
        val text = AccountHttp.execute(request = request)
        return ohMyJson.decodeFromString<MeResponse>(text)
    }

    @Serializable
    private data class ServerInfo(val salt: String)

    @Serializable
    private data class LoginResponse(val accessToken: String, val refreshToken: String)

    @Serializable
    private data class MeResponse(
        val id: String,
        val email: String,
        val publicKey: String,
        val privateKey: String,
        val secretKey: String,
        val teams: List<MeTeam>,
        val subscriptions: List<Subscription>,
    )


    @Serializable
    private data class MeTeam(val id: String, val name: String, val role: TeamRole, val secretKey: String)
}