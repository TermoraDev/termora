package app.termora.database

import app.termora.Application
import app.termora.ApplicationScope
import app.termora.randomUUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.lang3.RandomUtils
import org.apache.commons.lang3.StringUtils
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.security.MessageDigest
import java.util.UUID

internal class DatabaseSecret(private val database: Database) {
    companion object {
        const val PASSWORD = "__DB_PASSWORD"
        const val SALT = "__DB_SALT"
        const val PROTECTION_ENABLED = "__DB_PROTECTION_ENABLED"
        const val PROTECTION_ENVELOPE = "__DB_PROTECTION_ENVELOPE"
        const val DEVICE_ID = "__DB_DEVICE_ID"
        const val PROTECTED_PASSWORD_SENTINEL = "!TERMORA_PROTECTED_V1!"

        fun getInstance(database: Database): DatabaseSecret {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(DatabaseSecret::class) { DatabaseSecret(database) }
        }

        fun getInstance(): DatabaseSecret = ApplicationScope.forApplicationScope().get(DatabaseSecret::class)
    }

    @Volatile
    private var payload: DatabaseSecretPayload? = null

    @Volatile
    private var envelope: DatabaseSecretEnvelope? = null

    @Volatile
    private var secretState = DatabaseSecretState.UNINITIALIZED

    val password: String get() = requireUnlocked().password
    val salt: String get() = requireUnlocked().salt

    init {
        bootstrap()
    }

    fun state(): DatabaseSecretState = secretState

    fun requiresUnlock(): Boolean = secretState == DatabaseSecretState.LOCKED

    fun isProtectionEnabled(): Boolean = envelope != null

    fun requireUnlocked(): DatabaseSecretPayload {
        return payload ?: throw IllegalStateException("Database secret is locked or unavailable")
    }

    fun unlock(masterPassword: CharArray): UnlockResult {
        try {
            if (secretState == DatabaseSecretState.UNLOCKED) return UnlockResult.SUCCESS
            val protectedEnvelope = envelope ?: return UnlockResult.UNSUPPORTED_FORMAT
            val unlocked = DatabaseSecretCrypto.decrypt(protectedEnvelope, masterPassword)
            payload = unlocked
            secretState = DatabaseSecretState.UNLOCKED
            return UnlockResult.SUCCESS
        } catch (e: IllegalArgumentException) {
            return UnlockResult.UNSUPPORTED_FORMAT
        } catch (_: Exception) {
            return UnlockResult.WRONG_PASSWORD_OR_CORRUPT
        } finally {
            masterPassword.fill('\u0000')
        }
    }

    fun enable(masterPassword: CharArray) {
        try {
            check(secretState == DatabaseSecretState.UNLOCKED) { "Database secret must be unlocked" }
            val current = requireUnlocked()
            val deviceId = read(DEVICE_ID) ?: UUID.randomUUID().toString()
            val newEnvelope = DatabaseSecretCrypto.encrypt(current, masterPassword, randomUUID(), deviceId, 1)
            check(DatabaseSecretCrypto.decrypt(newEnvelope, masterPassword) == current) { "Database secret self-check failed" }
            transaction(database) {
                write(PROTECTION_ENVELOPE, Application.ohMyJson.encodeToString(newEnvelope))
                write(PROTECTION_ENABLED, "1")
                write(DEVICE_ID, deviceId)
                write(PASSWORD, PROTECTED_PASSWORD_SENTINEL)
                write(SALT, current.salt)
            }
            envelope = newEnvelope
        } finally {
            masterPassword.fill('\u0000')
        }
    }

    fun change(oldPassword: CharArray, newPassword: CharArray) {
        try {
            val oldEnvelope = envelope ?: throw IllegalStateException("Master password protection is not enabled")
            val current = DatabaseSecretCrypto.decrypt(oldEnvelope, oldPassword)
            check(payloadEquals(current, requireUnlocked())) { "Master password does not match current database secret" }
            val newEnvelope = DatabaseSecretCrypto.encrypt(
                current, newPassword, oldEnvelope.keyId, oldEnvelope.deviceId, oldEnvelope.revision + 1
            )
            check(DatabaseSecretCrypto.decrypt(newEnvelope, newPassword) == current) { "Database secret self-check failed" }
            transaction(database) { write(PROTECTION_ENVELOPE, Application.ohMyJson.encodeToString(newEnvelope)) }
            envelope = newEnvelope
        } finally {
            oldPassword.fill('\u0000')
            newPassword.fill('\u0000')
        }
    }

    fun disable(masterPassword: CharArray) {
        try {
            val oldEnvelope = envelope ?: throw IllegalStateException("Master password protection is not enabled")
            val current = DatabaseSecretCrypto.decrypt(oldEnvelope, masterPassword)
            check(payloadEquals(current, requireUnlocked())) { "Master password does not match current database secret" }
            transaction(database) {
                write(PASSWORD, current.password)
                write(SALT, current.salt)
                write(PROTECTION_ENABLED, "0")
                UnsafeSettingEntity.deleteWhere { UnsafeSettingEntity.name eq PROTECTION_ENVELOPE }
            }
            envelope = null
        } finally {
            masterPassword.fill('\u0000')
        }
    }

    private fun bootstrap() {
        transaction(database) {
            val enabled = read(PROTECTION_ENABLED) == "1"
            if (enabled) {
                val encoded = read(PROTECTION_ENVELOPE)
                if (encoded.isNullOrBlank()) {
                    secretState = DatabaseSecretState.FAILED
                    return@transaction
                }
                envelope = runCatching { Application.ohMyJson.decodeFromString<DatabaseSecretEnvelope>(encoded) }.getOrNull()
                secretState = if (envelope == null) DatabaseSecretState.FAILED else DatabaseSecretState.LOCKED
                return@transaction
            }

            val password = read(PASSWORD) ?: generateSecret(16).also { write(PASSWORD, it) }
            val salt = read(SALT) ?: generateSecret(12).also { write(SALT, it) }
            if (password == PROTECTED_PASSWORD_SENTINEL) {
                secretState = DatabaseSecretState.FAILED
            } else {
                payload = DatabaseSecretPayload(password, salt)
                secretState = DatabaseSecretState.UNLOCKED
            }
        }
    }

    private fun JdbcTransaction.read(name: String): String? {
        return UnsafeSettingEntity.selectAll().where { UnsafeSettingEntity.name eq name }
            .firstOrNull()?.get(UnsafeSettingEntity.value)
    }

    private fun read(name: String): String? = transaction(database) { read(name) }

    private fun JdbcTransaction.write(name: String, value: String) {
        if (read(name) == null) {
            UnsafeSettingEntity.insert {
                it[UnsafeSettingEntity.name] = name
                it[UnsafeSettingEntity.value] = value
            }
        } else {
            UnsafeSettingEntity.update({ UnsafeSettingEntity.name eq name }) { it[UnsafeSettingEntity.value] = value }
        }
    }

    private fun generateSecret(length: Int): String {
        return StringUtils.substring(DigestUtils.sha256Hex(RandomUtils.secureStrong().randomBytes(128)), 0, length)
    }

    private fun payloadEquals(first: DatabaseSecretPayload, second: DatabaseSecretPayload): Boolean {
        val firstBytes = Application.ohMyJson.encodeToString(first).toByteArray()
        val secondBytes = Application.ohMyJson.encodeToString(second).toByteArray()
        return try {
            MessageDigest.isEqual(firstBytes, secondBytes)
        } finally {
            firstBytes.fill(0)
            secondBytes.fill(0)
        }
    }
}
