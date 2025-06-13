package app.termora.database

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnWithTransform
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.crypt.Encryptor
import org.jetbrains.exposed.v1.crypt.StringEncryptionTransformer

fun Table.encryptedText(name: String, encryptor: Encryptor): Column<String> =
    registerColumn(name, EncryptedTextColumnType(encryptor))

class EncryptedTextColumnType(
    encryptor: Encryptor,
) : ColumnWithTransform<String, String>(TextColumnType(), StringEncryptionTransformer(encryptor))
