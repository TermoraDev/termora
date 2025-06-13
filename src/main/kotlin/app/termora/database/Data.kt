package app.termora.database

import app.termora.randomUUID
import org.jetbrains.exposed.v1.core.ResultRow

data class Data(
    val id: String = randomUUID(),
    val ownerId: String,
    val ownerType: String,
    val type: String,
    val data: String,
    val version: Long = 0,
    val synced: Boolean = false,
    val deleted: Boolean = false,
) {
    companion object {
        fun ResultRow.toData(): Data {
            return Data(
                id = this[DataEntity.id],
                ownerId = this[DataEntity.ownerId],
                ownerType = this[DataEntity.ownerType],
                type = this[DataEntity.type],
                data = this[DataEntity.data],
                version = this[DataEntity.version],
                synced = this[DataEntity.synced],
                deleted = this[DataEntity.deleted],
            )
        }
    }
}
