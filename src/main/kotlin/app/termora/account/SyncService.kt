package app.termora.account

import app.termora.AES
import app.termora.database.*
import app.termora.database.Data.Companion.toData
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

abstract class SyncService {

    companion object {
        private val syncLock = ReentrantLock()
    }

    protected val databaseManager get() = DatabaseManager.getInstance()
    private val database get() = databaseManager.database
    private val databaseLock get() = databaseManager.lock
    protected val accountManager get() = AccountManager.getInstance()
    protected val isFreePlan get() = accountManager.isFreePlan()

    /**
     * 同一时刻，要么拉取 要么推送
     */
    protected val syncLock get() = Companion.syncLock

    protected fun getData(id: String): Data? {
        val list = mutableListOf<Data>()
        databaseLock.withLock {
            transaction(database) {
                val rows = DataEntity.selectAll().where { (DataEntity.id.eq(id)) }.toList()
                for (row in rows) {
                    list.add(row.toData())
                }
            }
        }
        return list.firstOrNull()
    }


    protected fun updateData(
        id: String,
        synced: Boolean? = null,
        version: Long? = null,
        deleted: Boolean? = null,
    ) {
        if (ObjectUtils.allNull(version, deleted, synced)) return

        databaseLock.withLock {
            transaction(database) {
                DataEntity.update({ DataEntity.id.eq(id) }) {
                    if (version != null) it[DataEntity.version] = version
                    if (synced != null) it[DataEntity.synced] = synced
                    if (deleted != null) {
                        it[DataEntity.deleted] = deleted
                        it[DataEntity.data] = StringUtils.EMPTY
                    }
                }
            }
        }

        // 触发更改
        if (this is PullService) {
            DatabaseChangedExtension.fireDataChanged(
                id, DataType.Host.name,
                DatabaseChangedExtension.Action.Changed,
                DatabaseChangedExtension.Source.Sync
            )
        }
    }

    protected fun encryptData(id: String, data: String, ownerId: String): String {
        val secretKey = getSecretKey(ownerId)
        if (secretKey.isEmpty()) return StringUtils.EMPTY
        val nonce = AES.randomBytes(12)
        val ciphertext = AES.GCM.encrypt(secretKey, nonce, data.toByteArray())
        return "v2:" + Base64.encodeBase64String(nonce + ciphertext)
    }

    protected fun getSecretKey(ownerId: String): ByteArray {
        if (ownerId == accountManager.getAccountId()) {
            return accountManager.getSecretKey()
        }
        val team = accountManager.getTeams().firstOrNull { it.id == ownerId }
        return team?.secretKey ?: byteArrayOf()
    }

    protected fun decryptData(id: String, data: String, ownerId: String): String {
        val secretKey = getSecretKey(ownerId)
        if (secretKey.isEmpty()) throw IllegalStateException("根据 ownerId 无法获取对应密钥")
        if (data.startsWith("v2:")) {
            val encrypted = Base64.decodeBase64(data.removePrefix("v2:"))
            require(encrypted.size > 12) { "Invalid sync ciphertext" }
            return String(AES.GCM.decrypt(secretKey, encrypted.copyOf(12), encrypted.copyOfRange(12, encrypted.size)))
        }
        val legacyNonce = DigestUtils.sha256(id).copyOf(12)
        return String(AES.GCM.decrypt(secretKey, legacyNonce, Base64.decodeBase64(data)))
    }
}
