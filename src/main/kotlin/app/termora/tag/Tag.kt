package app.termora.tag

import kotlinx.serialization.Serializable

@Serializable
data class Tag(
    val id: String,
    val text: String,
    val createDate: Long = System.currentTimeMillis(),
    val updateDate: Long = System.currentTimeMillis(),
)