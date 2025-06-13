package app.termora.account

import kotlinx.serialization.Serializable

@Serializable
data class Server(val name: String, val server: String)