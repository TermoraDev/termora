package app.termora.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DatabaseSecretCryptoTest {
    private val payload = DatabaseSecretPayload("database-password", "database-salt")

    @Test
    fun `envelope round trip`() {
        val password = "correct horse battery staple".toCharArray()
        val envelope = DatabaseSecretCrypto.encrypt(payload, password, "key", "device", 1)
        assertEquals(payload, DatabaseSecretCrypto.decrypt(envelope, password))
    }

    @Test
    fun `wrong password and tampering fail closed`() {
        val envelope = DatabaseSecretCrypto.encrypt(payload, "master-password".toCharArray(), "key", "device", 1)
        assertFailsWith<Exception> { DatabaseSecretCrypto.decrypt(envelope, "wrong-password".toCharArray()) }
        assertFailsWith<Exception> {
            DatabaseSecretCrypto.decrypt(envelope.copy(ciphertext = envelope.ciphertext.drop(1) + "A"), "master-password".toCharArray())
        }
    }

    @Test
    fun `each envelope uses a unique nonce`() {
        val first = DatabaseSecretCrypto.encrypt(payload, "master-password".toCharArray(), "key", "device", 1)
        val second = DatabaseSecretCrypto.encrypt(payload, "master-password".toCharArray(), "key", "device", 1)
        assertFalse(first.cipher.nonce == second.cipher.nonce)
        assertFalse(first.ciphertext == second.ciphertext)
    }
}
