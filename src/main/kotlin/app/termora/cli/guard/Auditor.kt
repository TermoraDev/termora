package app.termora.cli.guard

import app.termora.Application
import app.termora.Application.ohMyJson
import kotlinx.serialization.Serializable
import java.io.File

/** 审计：每条操作追加一行 JSON 到文件。用独立文件而非数据库，规避 SQLite 写锁与 EDT 写检查。 */
class Auditor(private val file: File) {

    @Serializable
    data class Entry(
        val time: Long,
        val assetId: String,
        val host: String,
        val action: String,
        val command: String,
        val exitCode: Int,
    )

    @Synchronized
    fun record(assetId: String, host: String, action: String = "exec", command: String, exitCode: Int) {
        val entry = Entry(System.currentTimeMillis(), assetId, host, action, command, exitCode)
        file.parentFile?.mkdirs()
        file.appendText(ohMyJson.encodeToString(Entry.serializer(), entry) + "\n")
    }

    companion object {
        /** 生产路径：<baseDataDir>/cli-audit.log */
        fun defaultFile(): File = File(Application.getBaseDataDir(), "cli-audit.log")
    }
}
