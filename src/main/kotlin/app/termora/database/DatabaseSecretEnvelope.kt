package app.termora.database

import kotlinx.serialization.Serializable

@Serializable
data class DatabaseSecretPayload(
    val password: String,
    val salt: String,
)

@Serializable
data class KdfParams(
    val algorithm: String = "PBKDF2WithHmacSHA512",
    val salt: String,
    val iterations: Int = 220_000,
    val keyLength: Int = 256,
)

@Serializable
data class CipherParams(
    val algorithm: String = "AES/GCM/NoPadding",
    val nonce: String,
    val tagLength: Int = 128,
)

@Serializable
data class DatabaseSecretEnvelope(
    val schemaVersion: Int = 1,
    val keyId: String,
    val deviceId: String,
    val kdf: KdfParams,
    val cipher: CipherParams,
    val ciphertext: String,
    val revision: Long = 1,
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class DatabaseSecretState {
    UNINITIALIZED,
    LOCKED,
    UNLOCKED,
    FAILED,
}

enum class UnlockResult {
    SUCCESS,
    WRONG_PASSWORD_OR_CORRUPT,
    UNSUPPORTED_FORMAT,
}
