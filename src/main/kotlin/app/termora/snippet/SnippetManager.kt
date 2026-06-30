package app.termora.snippet

import app.termora.Application.ohMyJson
import app.termora.ApplicationScope
import app.termora.account.AccountManager
import app.termora.assertEventDispatchThread
import app.termora.database.Data
import app.termora.database.DataType
import app.termora.database.DatabaseManager
import app.termora.database.OwnerType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.nio.file.Files


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
    }

    /**
     * 第一次调用从数据库中获取，后续从缓存中获取
     */
    fun snippets(): List<Snippet> {
        return database.data<Snippet>(DataType.Snippet)
            .sortedWith(compareBy<Snippet> { if (it.type == SnippetType.Folder) 0 else 1 }.thenBy { it.sort })
    }

    /**
     * 导出所有代码片段到JSON文件
     */
    fun exportSnippets(file: File) {
        val snippets = snippets()
        val exportData = SnippetExportData(
            version = 1,
            exportDate = System.currentTimeMillis(),
            snippets = snippets.map { it.copy(deleted = false) } // 导出时不包含deleted标记
        )
        val json = ohMyJson.encodeToString(exportData)
        Files.writeString(file.toPath(), json)
    }

    /**
     * 从JSON文件导入代码片段
     * @param file 要导入的文件
     * @param replaceAll true=替换所有现有片段，false=合并（保留现有片段）
     * @return 导入的片段数量
     */
    fun importSnippets(file: File, replaceAll: Boolean): Int {
        assertEventDispatchThread()
        val json = Files.readString(file.toPath())
        val importData = ohMyJson.decodeFromString<SnippetExportData>(json)

        if (replaceAll) {
            // 删除所有现有片段
            snippets().forEach { removeSnippet(it.id) }
        }

        // 导入新片段
        importData.snippets.forEach { snippet ->
            addSnippet(snippet)
        }

        return importData.snippets.size
    }

    @Serializable
    private data class SnippetExportData(
        val version: Int,
        val exportDate: Long,
        val snippets: List<Snippet>
    )
}