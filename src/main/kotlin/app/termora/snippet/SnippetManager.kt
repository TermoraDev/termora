package app.termora.snippet

import app.termora.Application.ohMyJson
import app.termora.ApplicationScope
import app.termora.DeleteDataManager
import app.termora.account.AccountManager
import app.termora.assertEventDispatchThread
import app.termora.database.Data
import app.termora.database.DataType
import app.termora.database.DatabaseManager
import app.termora.database.OwnerType


class SnippetManager private constructor() {
    companion object {
        fun getInstance(): SnippetManager {
            return ApplicationScope.forApplicationScope().getOrCreate(SnippetManager::class) { SnippetManager() }
        }
    }

    private val database get() = DatabaseManager.getInstance()

    /**
     * 修改缓存并存入数据库
     */
    fun addSnippet(snippet: Snippet) {
        assertEventDispatchThread()
        if (snippet.deleted) {
            removeSnippet(snippet.id)
        } else {
            val accountId = AccountManager.getInstance().getAccountId()

            database.saveAndIncrementVersion(
                Data(
                    id = snippet.id,
                    ownerId = accountId,
                    ownerType = OwnerType.User.name,
                    type = DataType.Snippet.name,
                    data = ohMyJson.encodeToString(snippet),
                )
            )

        }
    }

    fun removeSnippet(id: String) {
        database.delete(id, DataType.Snippet.name)
        DeleteDataManager.getInstance().removeSnippet(id)
    }

    /**
     * 第一次调用从数据库中获取，后续从缓存中获取
     */
    fun snippets(): List<Snippet> {
        return database.data<Snippet>(DataType.Snippet)
            .sortedWith(compareBy<Snippet> { if (it.type == SnippetType.Folder) 0 else 1 }.thenBy { it.sort })
    }


}