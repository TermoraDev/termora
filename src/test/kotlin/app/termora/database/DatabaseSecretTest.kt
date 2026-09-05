package app.termora.database

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseSecretTest {
    @Test
    fun `disabling protection restores the original database secret`() {
        val database = temporaryDatabase()
        val secret = DatabaseSecret(database)
        val original = secret.requireUnlocked()

        secret.enable("master".toCharArray())
        assertTrue(secret.isProtectionEnabled())
        assertEquals(DatabaseSecret.PROTECTED_PASSWORD_SENTINEL, setting(database, DatabaseSecret.PASSWORD))

        secret.disable("master".toCharArray())
        assertFalse(secret.isProtectionEnabled())
        assertEquals(original.password, setting(database, DatabaseSecret.PASSWORD))
        assertEquals(original.salt, setting(database, DatabaseSecret.SALT))
        assertEquals("0", setting(database, DatabaseSecret.PROTECTION_ENABLED))
        assertEquals(null, setting(database, DatabaseSecret.PROTECTION_ENVELOPE))
    }

    @Test
    fun `wrong master password cannot disable protection`() {
        val database = temporaryDatabase()
        val secret = DatabaseSecret(database)
        secret.enable("master".toCharArray())

        assertFailsWith<Exception> { secret.disable("wrong".toCharArray()) }
        assertTrue(secret.isProtectionEnabled())
        assertEquals(DatabaseSecret.PROTECTED_PASSWORD_SENTINEL, setting(database, DatabaseSecret.PASSWORD))
    }

    private fun temporaryDatabase(): Database {
        val file = Files.createTempFile("termora-secret-", ".db").toFile()
        val database = Database.connect("jdbc:sqlite:${file.absolutePath}", driver = "org.sqlite.JDBC", user = "sa")
        transaction(database) { SchemaUtils.create(UnsafeSettingEntity) }
        return database
    }

    private fun setting(database: Database, name: String): String? {
        return transaction(database) {
            UnsafeSettingEntity.selectAll().where { UnsafeSettingEntity.name eq name }
                .firstOrNull()?.get(UnsafeSettingEntity.value)
        }
    }
}
