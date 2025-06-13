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
    val role: TeamRole,
)
