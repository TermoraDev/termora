package app.termora

import kotlinx.serialization.Serializable

@Serializable
data class ToolBarAction(
    val id: String,
    val visible: Boolean,
)