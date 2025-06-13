package app.termora.account

import app.termora.AntPathMatcher
import app.termora.Application
import app.termora.Application.ohMyJson
import app.termora.Ed25519
import app.termora.ResponseException
import app.termora.plugin.ExtensionManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.withLock
import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.lang3.time.DateUtils
import org.apache.commons.net.util.SubnetUtils
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import javax.swing.SwingUtilities

object AccountHttp {
    private val log = LoggerFactory.getLogger(AccountHttp::class.java)
    val client = Application.httpClient.newBuilder()
        .addInterceptor(UserAgentInterceptor())
        .addInterceptor(SignatureInterceptor())
        .addInterceptor(AccessTokenInterceptor())
        .build()


    fun execute(client: OkHttpClient = AccountHttp.client, request: Request): String {
        val response = client.newCall(request).execute()
        if (response.isSuccessful.not()) {
            IOUtils.closeQuietly(response)
            throw ResponseException(response.code, response)
        }

        val text = response.use { response.body.use { it?.string() } }
        if (text.isNullOrBlank()) {
            throw ResponseException(response.code, "response body is empty", response)
        }

        if (log.isDebugEnabled) {
            log.debug("url: ${request.url} response: $text")
        }

        return text
    }

    private class SignatureInterceptor : Interceptor {
        private val accountProperties get() = AccountProperties.getInstance()
        private val matcher = AntPathMatcher(".")

        // @formatter:off
        private val publicKey by lazy { Ed25519.generatePublic(Base64.decodeBase64("MCowBQYDK2VwAyEADhvgc8vWLXBFB36QtMlCujqdBNDMb2T5qE2V03hJKWA=")) }
        // @formatter:on

        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            val signatureBase64 = response.headers("X-Signature").firstOrNull()
            val dataBase64 = response.headers("X-Signature-Data").firstOrNull()
            var signed: Boolean? = null

            if (signatureBase64.isNullOrBlank() || dataBase64.isNullOrBlank()) {
                signed = false
            } else {
                val signature = Base64.decodeBase64(signatureBase64)
                val data = Base64.decodeBase64(dataBase64)
                val json = ohMyJson.decodeFromString<JsonObject>(String(data))

                // 校验许可证日期
                val subscriptionExpiry = json["SubscriptionExpiry"]?.jsonPrimitive?.content
                if (subscriptionExpiry == null) {
                    signed = false
                } else {
                    val date = runCatching { DateUtils.parseDate(subscriptionExpiry, "yyyy-MM-dd") }.getOrNull()
                    if (date == null) {
                        signed = false
                    } else if (Application.getReleaseDate().after(date) || date.time < System.currentTimeMillis()) {
                        signed = false
                    }
                }

                // 校验 host 是否与签名一致
                if (signed == null) {
                    if (isInRange(chain.request(), json).not()) {
                        signed = false
                    }
                }

                // 校验许可证签名
                if (signed == null) {
                    signed = Ed25519.verify(publicKey, data, signature)
                }
            }

            val oldSigned = accountProperties.signed

            if (oldSigned != signed) {
                accountProperties.signed = signed
                SwingUtilities.invokeLater {
                    for (extension in ExtensionManager.getInstance().getExtensions(ServerSignedExtension::class.java)) {
                        extension.onSignedChanged(oldSigned, signed)
                    }
                }
            }

            return response
        }

        private fun isInRange(request: Request, json: JsonObject): Boolean {
            val hostsArray = json["Hosts"]?.jsonArray
            if (hostsArray.isNullOrEmpty()) {
                return false
            }

            val host = request.url.host
            val hosts = ohMyJson.decodeFromJsonElement<List<String>>(hostsArray).toMutableList()
            hosts.addFirst("127.0.0.1")
            hosts.addFirst("localhost")
            for (cidr in hosts) {
                try {
                    if (cidr == host) {
                        return true
                    }

                    if (matcher.match(cidr, host)) {
                        return true
                    }

                    val subnet = SubnetUtils(cidr)
                    if (subnet.info.isInRange(host)) {
                        return true
                    }

                } catch (e: Exception) {
                    if (log.isDebugEnabled) {
                        log.debug(e.message, e)
                    }
                }
            }



            return false
        }
    }

    private class UserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val builder = chain.request().newBuilder()
            if (chain.request().header("User-Agent") == null) {
                builder.header("User-Agent", Application.getUserAgent())
            }
            return chain.proceed(builder.build())
        }

    }

    private class AccessTokenInterceptor : Interceptor {
        private val lock = ReentrantLock()
        private val condition = lock.newCondition()
        private val isRefreshing = AtomicBoolean(false)

        override fun intercept(chain: Interceptor.Chain): Response {
            val builder = chain.request().newBuilder()
            val accountManager = AccountManager.getInstance()
            val accessToken = accountManager.getAccessToken()
            if (chain.request().header("Authorization") == null) {
                if (accessToken.isNotBlank()) {
                    builder.header("Authorization", "Bearer $accessToken")
                }
            }

            val response = chain.proceed(builder.build())
            if (response.code == 401 && accountManager.isLocally().not()) {
                IOUtils.closeQuietly(response)

                if (isRefreshing.compareAndSet(false, true)) {
                    try {
                        // 刷新 token
                        accountManager.refreshToken()
                    } finally {
                        lock.withLock {
                            isRefreshing.set(false)
                            condition.signalAll()
                        }
                    }
                } else {
                    lock.lock()
                    try {
                        condition.await()
                    } finally {
                        lock.unlock()
                    }
                }

                // 拿到新 token 后重新发请求
                val newAccessToken = accountManager.getAccessToken()
                val newRequest = builder
                    .removeHeader("Authorization")
                    .header("Authorization", "Bearer $newAccessToken")
                    .build()

                return chain.proceed(newRequest)

            }

            return response
        }
    }
}