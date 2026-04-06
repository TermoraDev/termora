package app.termora.account

import kotlinx.serialization.Serializable

@Serializable
data class Subscription(
    val id: String,
    val plan: String,
    val startAt: Long,
    val endAt: Long,
)
