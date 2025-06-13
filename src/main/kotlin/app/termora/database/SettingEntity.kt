package app.termora.database

import app.termora.LocalSecret
import app.termora.randomUUID
import org.apache.commons.lang3.StringUtils
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.crypt.Algorithms

object SettingEntity : Table() {
    val id: Column<String> = char("id", length = 32).clientDefault { randomUUID() }
    val name: Column<String> = varchar("name", length = 128).index()
    val value: Column<String> = encryptedText(
        "value", Algorithms.AES_256_PBE_GCM(
            LocalSecret.getInstance().password,
            LocalSecret.getInstance().salt
        )
    )

    /**
     * 备用字段1-3
     */
    val extra1: Column<String> = text("extra1").clientDefault { StringUtils.EMPTY }
    val extra2: Column<String> = text("extra2").clientDefault { StringUtils.EMPTY }
    val extra3: Column<String> = text("extra3").clientDefault { StringUtils.EMPTY }

    override val primaryKey: PrimaryKey get() = PrimaryKey(id)
    override val tableName: String
        get() = "tb_setting"
}
