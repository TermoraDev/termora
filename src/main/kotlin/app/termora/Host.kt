package app.termora

import kotlinx.serialization.Serializable
import org.apache.commons.lang3.StringUtils
import java.util.*


fun UUID.toSimpleString(): String {
    return toString().replace("-", StringUtils.EMPTY)
}

enum class Protocol {
    Folder,
    SSH,
    Local,
}


enum class AuthenticationType {
    No,
    Password,
    PublicKey,
    KeyboardInteractive,
}

enum class ProxyType {
    No,
    HTTP,
    SOCKS5,
}

@Serializable
data class Authentication(
    val type: AuthenticationType,
    val password: String,
) {
    companion object {
        val No = Authentication(AuthenticationType.No, String())
    }
}


@Serializable
data class Options(
    /**
     * 跳板机
     */
    val jumpHosts: List<String> = mutableListOf(),
    /**
     * 编码
     */
    val encoding: String = "UTF-8",
    /**
     * 环境变量
     */
    val env: String = StringUtils.EMPTY,
    /**
     * 连接成功后立即发送命令
     */
    val startupCommand: String = StringUtils.EMPTY,
    /**
     * SSH 心跳间隔
     */
    val heartbeatInterval: Int = 30
) {
    companion object {
        val Default = Options()
    }

    fun envs(): Map<String, String> {
        if (env.isBlank()) return emptyMap()
        val envs = mutableMapOf<String, String>()
        for (line in env.lines()) {
            if (line.isBlank()) continue
            val vars = line.split("=", limit = 2)
            if (vars.size != 2) continue
            envs[vars[0]] = vars[1]
        }
        return envs
    }
}

@Serializable
data class Proxy(
    val type: ProxyType,
    val host: String,
    val port: Int,
    val authenticationType: AuthenticationType = AuthenticationType.No,
    val username: String,
    val password: String,
) {
    companion object {
        val No = Proxy(
            ProxyType.No,
            host = StringUtils.EMPTY,
            port = 7890,
            username = StringUtils.EMPTY,
            password = StringUtils.EMPTY
        )
    }
}

enum class TunnelingType {
    Local,
    Remote,
    Dynamic
}

@Serializable
data class Tunneling(
    val name: String = StringUtils.EMPTY,
    val type: TunnelingType = TunnelingType.Local,
    val sourceHost: String = StringUtils.EMPTY,
    val sourcePort: Int = 0,
    val destinationHost: String = StringUtils.EMPTY,
    val destinationPort: Int = 0,
)


@Serializable
data class EncryptedHost(
    var id: String = StringUtils.EMPTY,
    var name: String = StringUtils.EMPTY,
    var protocol: String = StringUtils.EMPTY,
    var host: String = StringUtils.EMPTY,
    var port: String = StringUtils.EMPTY,
    var username: String = StringUtils.EMPTY,
    var remark: String = StringUtils.EMPTY,
    var authentication: String = StringUtils.EMPTY,
    var proxy: String = StringUtils.EMPTY,
    var options: String = StringUtils.EMPTY,
    var tunnelings: String = StringUtils.EMPTY,
    var sort: Long = 0L,
    var deleted: Boolean = false,
    var parentId: String = StringUtils.EMPTY,
    var ownerId: String = StringUtils.EMPTY,
    var creatorId: String = StringUtils.EMPTY,
    var createDate: Long = 0L,
    var updateDate: Long = 0L,
)


@Serializable
data class Host(
    /**
     * 唯一ID
     */
    val id: String = UUID.randomUUID().toSimpleString(),
    /**
     * 名称
     */
    val name: String,
    /**
     * 协议
     */
    val protocol: Protocol,
    /**
     * 主机
     */
    val host: String = StringUtils.EMPTY,
    /**
     * 端口
     */
    val port: Int = 0,
    /**
     * 用户名
     */
    val username: String = StringUtils.EMPTY,
    /**
     * 备注
     */
    val remark: String = StringUtils.EMPTY,
    /**
     * 认证信息
     */
    val authentication: Authentication = Authentication.No,
    /**
     * 代理
     */
    val proxy: Proxy = Proxy.No,

    /**
     * 选项，备用字段
     */
    val options: Options = Options.Default,

    /**
     * 隧道
     */
    val tunnelings: List<Tunneling> = emptyList(),

    /**
     * 排序
     */
    val sort: Long = 0,
    /**
     * 父ID
     */
    val parentId: String = "0",
    /**
     * 所属者
     */
    val ownerId: String = "0",
    /**
     * 创建者
     */
    val creatorId: String = "0",
    /**
     * 创建时间
     */
    val createDate: Long = System.currentTimeMillis(),
    /**
     * 更新时间
     */
    val updateDate: Long = System.currentTimeMillis(),

    /**
     * 是否已经删除
     */
    val deleted: Boolean = false
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Host

        if (id != other.id) return false
        if (ownerId != other.ownerId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + ownerId.hashCode()
        return result
    }
}