package app.termora.database

import app.termora.AES
import app.termora.Application
import kotlinx.serialization.encodeToString
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object DatabaseSecretCrypto {
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA512"
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    private const val KDF_SALT_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val KEY_LENGTH = 256
    private const val TAG_LENGTH = 128
    private const val ITERATIONS = 220_000
    private val random = SecureRandom()

    fun encrypt(
        payload: DatabaseSecretPayload,
        masterPassword: CharArray,
        keyId: String,
        deviceId: String,
        revision: Long,
    ): DatabaseSecretEnvelope {
        val salt = ByteArray(KDF_SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val kdf = KdfParams(salt = AES.run { salt.encodeBase64String() })
        val cipher = CipherParams(nonce = AES.run { nonce.encodeBase64String() })
        var kek: ByteArray? = null
        var plaintext: ByteArray? = null
        try {
            kek = deriveKey(masterPassword, kdf)
            plaintext = Application.ohMyJson.encodeToString(payload).toByteArray(StandardCharsets.UTF_8)
            val aes = Cipher.getInstance(CIPHER_ALGORITHM)
            aes.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(TAG_LENGTH, nonce))
            aes.updateAAD(aad(keyId, deviceId))
            return DatabaseSecretEnvelope(
                keyId = keyId,
                deviceId = deviceId,
                kdf = kdf,
                cipher = cipher,
                ciphertext = AES.run { aes.doFinal(plaintext).encodeBase64String() },
                revision = revision,
            )
        } finally {
            kek?.fill(0)
            plaintext?.fill(0)
            salt.fill(0)
            nonce.fill(0)
        }
    }

    fun decrypt(envelope: DatabaseSecretEnvelope, masterPassword: CharArray): DatabaseSecretPayload {
        validate(envelope)
        var kek: ByteArray? = null
        var plaintext: ByteArray? = null
        var nonce: ByteArray? = null
        var ciphertext: ByteArray? = null
        try {
            kek = deriveKey(masterPassword, envelope.kdf)
            val aes = Cipher.getInstance(CIPHER_ALGORITHM)
            nonce = AES.run { envelope.cipher.nonce.decodeBase64() }
            aes.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(kek, "AES"),
                GCMParameterSpec(TAG_LENGTH, nonce)
            )
            aes.updateAAD(aad(envelope.keyId, envelope.deviceId))
            ciphertext = AES.run { envelope.ciphertext.decodeBase64() }
            plaintext = aes.doFinal(ciphertext)
            return Application.ohMyJson.decodeFromString(plaintext.toString(StandardCharsets.UTF_8))
        } finally {
            kek?.fill(0)
            plaintext?.fill(0)
            nonce?.fill(0)
            ciphertext?.fill(0)
        }
    }

    private fun deriveKey(masterPassword: CharArray, params: KdfParams): ByteArray {
        val salt = AES.run { params.salt.decodeBase64() }
        val spec = PBEKeySpec(masterPassword, salt, params.iterations, params.keyLength)
        return try {
            SecretKeyFactory.getInstance(params.algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            salt.fill(0)
        }
    }

    private fun validate(envelope: DatabaseSecretEnvelope) {
        require(envelope.schemaVersion == 1) { "Unsupported database secret envelope version" }
        require(envelope.kdf.algorithm == KDF_ALGORITHM) { "Unsupported database secret KDF" }
        require(envelope.kdf.iterations > 0 && envelope.kdf.keyLength == KEY_LENGTH) { "Invalid database secret KDF parameters" }
        require(envelope.cipher.algorithm == CIPHER_ALGORITHM && envelope.cipher.tagLength == TAG_LENGTH) {
            "Unsupported database secret cipher"
        }
        require(envelope.keyId.isNotBlank() && envelope.deviceId.isNotBlank()) { "Invalid database secret envelope" }
    }

    private fun aad(keyId: String, deviceId: String): ByteArray {
        return "Termora|DatabaseSecretEnvelope|v1|$keyId|$deviceId".toByteArray(StandardCharsets.UTF_8)
    }
}
