package app.termora.cli

import kotlinx.serialization.Serializable

/** 暴露给外部的主机信息——绝不含任何凭证字段 */
@Serializable
data class AssetInfo(
    val id: String,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val username: String,
    val remark: String,
)

/** assets list 的 JSON 包装：{"assets":[...]} */
@Serializable
data class AssetList(val assets: List<AssetInfo>)
