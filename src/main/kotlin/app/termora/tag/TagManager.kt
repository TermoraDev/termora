package app.termora.tag

import app.termora.Application.ohMyJson
import app.termora.ApplicationScope
import app.termora.HostManager
import app.termora.account.AccountOwner
import app.termora.database.Data
import app.termora.database.DataType
import app.termora.database.DatabaseChangedExtension
import app.termora.database.DatabaseManager
import org.apache.commons.lang3.StringUtils

class TagManager private constructor() {
    companion object {
        fun getInstance(): TagManager {
            return ApplicationScope.forApplicationScope().getOrCreate(TagManager::class) { TagManager() }
        }
    }

    private val databaseManager get() = DatabaseManager.getInstance()
    private val hostManager get() = HostManager.getInstance()

    fun addTag(tag: Tag, accountOwner: AccountOwner) {

        databaseManager.saveAndIncrementVersion(
            Data(
                id = tag.id,
                ownerId = accountOwner.id,
                ownerType = accountOwner.type.name,
                type = DataType.Tag.name,
                data = ohMyJson.encodeToString(tag),
            )
        )
    }

    fun removeTag(id: String) {
        val data = databaseManager.data(id) ?: return
        for (host in hostManager.hosts()) {
            if (host.ownerId != data.ownerId) continue
            val tags = host.options.tags
            if (tags.contains(id)) {
                val c = tags.toMutableList()
                c.removeIf { it == id }
                // 来源改成 Sync 触发 reload
                hostManager.addHost(
                    host.copy(options = host.options.copy(tags = c)),
                    DatabaseChangedExtension.Source.Sync
                )
            }
        }
        databaseManager.delete(id, DataType.Tag.name)
    }

    fun getTags(ownerId: String = StringUtils.EMPTY): List<Tag> {
        return databaseManager.data<Tag>(DataType.Tag, ownerId).sortedBy { it.createDate }
    }


}