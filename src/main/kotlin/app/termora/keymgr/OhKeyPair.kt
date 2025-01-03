package app.termora.keymgr

import kotlinx.serialization.Serializable

@Serializable
data class OhKeyPair(
    val id: String,
    // base64
    val publicKey: String,
    // base64
    val privateKey: String,
    // RSA、ED25519
    val type: String,
    val name: String,
    val remark: String,
    val length: Int,
    val sort: Long,
) {
    companion object {
        val empty = OhKeyPair(String(), String(), String(), String(), String(), String(), 0, 0)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OhKeyPair

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }


}