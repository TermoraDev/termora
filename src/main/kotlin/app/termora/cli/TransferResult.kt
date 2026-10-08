package app.termora.cli

import kotlinx.serialization.Serializable

@Serializable
data class TransferResult(val ok: Boolean, val bytes: Long, val host: String)
