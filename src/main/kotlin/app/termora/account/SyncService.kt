package app.termora.account

import app.termora.AES
import app.termora.database.*
import app.termora.database.Data.Companion.toData
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
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

    protected fun encryptData(id: String, data: String): String {
        val iv = DigestUtils.sha256(id).copyOf(12)
        return Base64.encodeBase64String(
            AES.GCM.encrypt(
                accountManager.getSecretKey(), iv,
                data.toByteArray()
            )
        )
    }

    protected fun decryptData(id: String, data: String): String {
        val iv = DigestUtils.sha256(id).copyOf(12)
        return String(
            AES.GCM.decrypt(
                accountManager.getSecretKey(), iv,
                Base64.decodeBase64(data)
            )
        )
    }
}