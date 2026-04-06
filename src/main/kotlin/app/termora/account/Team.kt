package app.termora.account

import kotlinx.serialization.Serializable

/**
 * 团队
 */
@Serializable
class Team(
    /**
     * ID
     */
    val id: String,

    /**
     * 团队名称
     */
    val name: String,

    /**
     * 团队密钥，用于解密团队数据
     */
    val secretKey: ByteArray,

    /**
     * 所属角色
     */
    val role: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Team

        if (id != other.id) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}
