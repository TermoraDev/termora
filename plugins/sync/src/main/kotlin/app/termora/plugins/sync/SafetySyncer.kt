package app.termora.plugins.sync

import app.termora.*
import app.termora.AES.CBC.aesCBCDecrypt
import app.termora.AES.CBC.aesCBCEncrypt
import app.termora.AES.decodeBase64
import app.termora.AES.encodeBase64String
import app.termora.Application.ohMyJson
import app.termora.account.AccountManager
import app.termora.account.AccountOwner
import app.termora.database.OwnerType
import app.termora.highlight.KeywordHighlight
import app.termora.highlight.KeywordHighlightManager
import app.termora.keymap.Keymap
import app.termora.keymap.KeymapManager
import app.termora.keymgr.KeyManager
import app.termora.keymgr.OhKeyPair
import app.termora.macro.Macro
import app.termora.macro.MacroManager
import app.termora.snippet.Snippet
import app.termora.snippet.SnippetManager
import kotlinx.serialization.json.JsonObject
import org.apache.commons.lang3.ArrayUtils
import org.slf4j.LoggerFactory
import javax.swing.SwingUtilities

abstract class SafetySyncer : Syncer {
    companion object {
        private val log = LoggerFactory.getLogger(SafetySyncer::class.java)
    }

    protected val description = "${Application.getName()} config"
    protected val httpClient get() = Application.httpClient
    protected val hostManager get() = HostManager.getInstance()
    protected val keyManager get() = KeyManager.getInstance()
    protected val keywordHighlightManager get() = KeywordHighlightManager.getInstance()
    protected val macroManager get() = MacroManager.getInstance()
    protected val keymapManager get() = KeymapManager.getInstance()
    protected val snippetManager get() = SnippetManager.getInstance()
    protected val deleteDataManager get() = DeleteDataManager.getInstance()
    protected val accountManager get() = AccountManager.getInstance()
    protected val accountOwner
        get() = AccountOwner(
            id = accountManager.getAccountId(),
            name = accountManager.getEmail(),
            type = OwnerType.User
        )

    protected fun decodeHosts(text: String, deletedData: List<DeletedData>, config: SyncConfig) {
        // aes key
        val key = getKey(config)
        val encryptedHosts = ohMyJson.decodeFromString<List<EncryptedHost>>(text)
        val hosts = hostManager.hosts().associateBy { it.id }

        for (encryptedHost in encryptedHosts) {
            val oldHost = hosts[encryptedHost.id]

            // 如果本地的修改时间大于云端时间，那么跳过
            if (oldHost != null) {
                if (oldHost.updateDate >= encryptedHost.updateDate) {
                    continue
                }
            }

            try {
                // aes iv
                val iv = getIv(encryptedHost.id)
                val host = Host(
                    id = encryptedHost.id,
                    name = encryptedHost.name.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    protocol = encryptedHost.protocol.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    host = encryptedHost.host.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    port = encryptedHost.port.decodeBase64().aesCBCDecrypt(key, iv)
                        .decodeToString().toIntOrNull() ?: 0,
                    username = encryptedHost.username.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    remark = encryptedHost.remark.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    authentication = ohMyJson.decodeFromString(
                        encryptedHost.authentication.decodeBase64().aesCBCDecrypt(key, iv).decodeToString()
                    ),
                    proxy = ohMyJson.decodeFromString(
                        encryptedHost.proxy.decodeBase64().aesCBCDecrypt(key, iv).decodeToString()
                    ),
                    options = ohMyJson.decodeFromString(
                        encryptedHost.options.decodeBase64().aesCBCDecrypt(key, iv).decodeToString()
                    ),
                    tunnelings = ohMyJson.decodeFromString(
                        encryptedHost.tunnelings.decodeBase64().aesCBCDecrypt(key, iv).decodeToString()
                    ),
                    sort = encryptedHost.sort,
                    parentId = encryptedHost.parentId.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    ownerId = encryptedHost.ownerId.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    creatorId = encryptedHost.creatorId.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    createDate = encryptedHost.createDate,
                    updateDate = encryptedHost.updateDate,
                )
                SwingUtilities.invokeLater { hostManager.addHost(host) }
            } catch (e: Exception) {
                if (log.isWarnEnabled) {
                    log.warn("Decode host: ${encryptedHost.id} failed. error: {}", e.message, e)
                }
            }
        }

        SwingUtilities.invokeLater {
            deletedData.forEach {
                hostManager.removeHost(it.id)
                deleteDataManager.removeHost(it.id, it.deleteDate)
            }
        }

        if (log.isDebugEnabled) {
            log.debug("Decode hosts: {}", text)
        }
    }

    protected fun encodeHosts(key: ByteArray): String {
        val encryptedHosts = mutableListOf<EncryptedHost>()
        for (host in hostManager.hosts()) {
            // aes iv
            val iv = ArrayUtils.subarray(host.id.padEnd(16, '0').toByteArray(), 0, 16)
            val encryptedHost = EncryptedHost()
            encryptedHost.id = host.id
            encryptedHost.name = host.name.aesCBCEncrypt(key, iv).encodeBase64String()
            encryptedHost.protocol = host.protocol.aesCBCEncrypt(key, iv).encodeBase64String()
            encryptedHost.host = host.host.aesCBCEncrypt(key, iv).encodeBase64String()
            encryptedHost.port = "${host.port}".aesCBCEncrypt(key, iv).encodeBase64String()
            encryptedHost.username = host.username.aesCBCEncrypt(key, iv).encodeBase64String()
            encryptedHost.remark = host.remark.aesCBCEncrypt(key, iv).encodeBase64String()
            encryptedHost.authentication = ohMyJson.encodeToString(host.authentication)
                .aesCBCEncrypt(key, iv).encodeBase64String()
            encryptedHost.proxy = ohMyJson.encodeToString(host.proxy).aesCBCEncrypt(key, iv).encodeBase64String()
            encryptedHost.options =
                ohMyJson.encodeToString(host.options).aesCBCEncrypt(key, iv).encodeBase64String()
            encryptedHost.tunnelings =
                ohMyJson.encodeToString(host.tunnelings).aesCBCEncrypt(key, iv).encodeBase64String()
            encryptedHost.sort = host.sort
            encryptedHost.parentId = host.parentId.aesCBCEncrypt(key, iv).encodeBase64String()
            encryptedHost.ownerId = host.ownerId.aesCBCEncrypt(key, iv).encodeBase64String()
            encryptedHost.creatorId = host.creatorId.aesCBCEncrypt(key, iv).encodeBase64String()
            encryptedHost.createDate = host.createDate
            encryptedHost.updateDate = host.updateDate
            encryptedHosts.add(encryptedHost)
        }

        return ohMyJson.encodeToString(encryptedHosts)

    }

    protected fun encodeDeletedData(config: SyncConfig): String {
        return ohMyJson.encodeToString(deleteDataManager.getDeletedData())
    }

    protected fun decodeDeletedData(text: String, config: SyncConfig): List<DeletedData> {
        val deletedData = ohMyJson.decodeFromString<List<DeletedData>>(text).toMutableList()
        // 和本地融合
        deletedData.addAll(deleteDataManager.getDeletedData())
        return deletedData
    }

    protected fun decodeSnippets(text: String, deletedData: List<DeletedData>, config: SyncConfig) {
        // aes key
        val key = getKey(config)
        val encryptedSnippets = ohMyJson.decodeFromString<List<Snippet>>(text)
        val snippets = snippetManager.snippets().associateBy { it.id }

        for (encryptedSnippet in encryptedSnippets) {
            val oldHost = snippets[encryptedSnippet.id]

            // 如果一样，则无需配置
            if (oldHost != null) {
                if (oldHost.updateDate >= encryptedSnippet.updateDate) {
                    continue
                }
            }

            try {
                // aes iv
                val iv = getIv(encryptedSnippet.id)
                val snippet = encryptedSnippet.copy(
                    name = encryptedSnippet.name.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    parentId = encryptedSnippet.parentId.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    snippet = encryptedSnippet.snippet.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                )
                SwingUtilities.invokeLater { snippetManager.addSnippet(snippet) }
            } catch (e: Exception) {
                if (log.isWarnEnabled) {
                    log.warn("Decode snippet: ${encryptedSnippet.id} failed. error: {}", e.message, e)
                }
            }
        }

        SwingUtilities.invokeLater {
            deletedData.forEach {
                snippetManager.removeSnippet(it.id)
                deleteDataManager.removeSnippet(it.id, it.deleteDate)
            }
        }

        if (log.isDebugEnabled) {
            log.debug("Decode Snippets: {}", text)
        }
    }

    protected fun encodeSnippets(key: ByteArray): String {
        val snippets = mutableListOf<Snippet>()
        for (snippet in snippetManager.snippets()) {
            // aes iv
            val iv = ArrayUtils.subarray(snippet.id.padEnd(16, '0').toByteArray(), 0, 16)
            snippets.add(
                snippet.copy(
                    name = snippet.name.aesCBCEncrypt(key, iv).encodeBase64String(),
                    snippet = snippet.snippet.aesCBCEncrypt(key, iv).encodeBase64String(),
                    parentId = snippet.parentId.aesCBCEncrypt(key, iv).encodeBase64String(),
                )
            )
        }
        return ohMyJson.encodeToString(snippets)

    }

    protected fun decodeKeys(text: String, deletedData: List<DeletedData>, config: SyncConfig) {
        // aes key
        val key = getKey(config)
        val encryptedKeys = ohMyJson.decodeFromString<List<OhKeyPair>>(text)
        val keys = keyManager.getOhKeyPairs().associateBy { it.id }

        for (encryptedKey in encryptedKeys) {
            val k = keys[encryptedKey.id]
            if (k != null) {
                if (k.updateDate > encryptedKey.updateDate) {
                    continue
                }
            }

            try {
                // aes iv
                val iv = getIv(encryptedKey.id)
                val keyPair = OhKeyPair(
                    id = encryptedKey.id,
                    publicKey = encryptedKey.publicKey.decodeBase64().aesCBCDecrypt(key, iv).encodeBase64String(),
                    privateKey = encryptedKey.privateKey.decodeBase64().aesCBCDecrypt(key, iv).encodeBase64String(),
                    type = encryptedKey.type.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    name = encryptedKey.name.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    remark = encryptedKey.remark.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    length = encryptedKey.length,
                    sort = encryptedKey.sort
                )
                SwingUtilities.invokeLater { keyManager.addOhKeyPair(keyPair, accountOwner) }
            } catch (e: Exception) {
                if (log.isWarnEnabled) {
                    log.warn("Decode key: ${encryptedKey.id} failed. error: {}", e.message, e)
                }
            }
        }

        SwingUtilities.invokeLater {
            deletedData.forEach {
                keyManager.removeOhKeyPair(it.id)
                deleteDataManager.removeKeyPair(it.id, it.deleteDate)
            }
        }

        if (log.isDebugEnabled) {
            log.debug("Decode keys: {}", text)
        }
    }

    protected fun encodeKeys(key: ByteArray): String {
        val encryptedKeys = mutableListOf<OhKeyPair>()
        for (keyPair in keyManager.getOhKeyPairs()) {
            // aes iv
            val iv = ArrayUtils.subarray(keyPair.id.padEnd(16, '0').toByteArray(), 0, 16)
            val encryptedKeyPair = OhKeyPair(
                id = keyPair.id,
                publicKey = keyPair.publicKey.decodeBase64().aesCBCEncrypt(key, iv).encodeBase64String(),
                privateKey = keyPair.privateKey.decodeBase64().aesCBCEncrypt(key, iv).encodeBase64String(),
                type = keyPair.type.aesCBCEncrypt(key, iv).encodeBase64String(),
                name = keyPair.name.aesCBCEncrypt(key, iv).encodeBase64String(),
                remark = keyPair.remark.aesCBCEncrypt(key, iv).encodeBase64String(),
                length = keyPair.length,
                sort = keyPair.sort
            )
            encryptedKeys.add(encryptedKeyPair)
        }
        return ohMyJson.encodeToString(encryptedKeys)
    }

    protected fun decodeKeywordHighlights(text: String, deletedData: List<DeletedData>, config: SyncConfig) {
        // aes key
        val key = getKey(config)
        val encryptedKeywordHighlights = ohMyJson.decodeFromString<List<KeywordHighlight>>(text)
        val keywordHighlights = keywordHighlightManager.getKeywordHighlights().associateBy { it.id }

        for (e in encryptedKeywordHighlights) {
            val keywordHighlight = keywordHighlights[e.id]
            if (keywordHighlight != null) {
                if (keywordHighlight.updateDate >= e.updateDate) {
                    continue
                }
            }

            try {
                // aes iv
                val iv = getIv(e.id)
                keywordHighlightManager.addKeywordHighlight(
                    e.copy(
                        keyword = e.keyword.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                        description = e.description.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    ), accountOwner
                )
            } catch (ex: Exception) {
                if (log.isWarnEnabled) {
                    log.warn("Decode KeywordHighlight: ${e.id} failed. error: {}", ex.message, ex)
                }
            }
        }

        SwingUtilities.invokeLater {
            deletedData.forEach {
                keywordHighlightManager.removeKeywordHighlight(it.id)
                deleteDataManager.removeKeywordHighlight(it.id, it.deleteDate)
            }
        }

        if (log.isDebugEnabled) {
            log.debug("Decode KeywordHighlight: {}", text)
        }
    }

    protected fun encodeKeywordHighlights(key: ByteArray): String {
        val keywordHighlights = mutableListOf<KeywordHighlight>()
        for (keywordHighlight in keywordHighlightManager.getKeywordHighlights()) {
            // aes iv
            val iv = getIv(keywordHighlight.id)
            val encryptedKeyPair = keywordHighlight.copy(
                keyword = keywordHighlight.keyword.aesCBCEncrypt(key, iv).encodeBase64String(),
                description = keywordHighlight.description.aesCBCEncrypt(key, iv).encodeBase64String(),
            )
            keywordHighlights.add(encryptedKeyPair)
        }
        return ohMyJson.encodeToString(keywordHighlights)
    }

    protected fun decodeMacros(text: String, deletedData: List<DeletedData>, config: SyncConfig) {
        // aes key
        val key = getKey(config)
        val encryptedMacros = ohMyJson.decodeFromString<List<Macro>>(text)
        val macros = macroManager.getMacros().associateBy { it.id }
        for (e in encryptedMacros) {
            val macro = macros[e.id]
            if (macro != null) {
                if (macro.updateDate >= e.updateDate) {
                    continue
                }
            }

            try {
                // aes iv
                val iv = getIv(e.id)
                macroManager.addMacro(
                    e.copy(
                        name = e.name.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                        macro = e.macro.decodeBase64().aesCBCDecrypt(key, iv).decodeToString(),
                    )
                )
            } catch (ex: Exception) {
                if (log.isWarnEnabled) {
                    log.warn("Decode Macro: ${e.id} failed. error: {}", ex.message, ex)
                }
            }
        }

        SwingUtilities.invokeLater {
            deletedData.forEach {
                macroManager.removeMacro(it.id)
                deleteDataManager.removeMacro(it.id, it.deleteDate)
            }
        }

        if (log.isDebugEnabled) {
            log.debug("Decode Macros: {}", text)
        }
    }

    protected fun encodeMacros(key: ByteArray): String {
        val macros = mutableListOf<Macro>()
        for (macro in macroManager.getMacros()) {
            val iv = getIv(macro.id)
            macros.add(
                macro.copy(
                    name = macro.name.aesCBCEncrypt(key, iv).encodeBase64String(),
                    macro = macro.macro.aesCBCEncrypt(key, iv).encodeBase64String()
                )
            )
        }
        return ohMyJson.encodeToString(macros)
    }

    protected fun decodeKeymaps(text: String, deletedData: List<DeletedData>, config: SyncConfig) {

        val localKeymaps = keymapManager.getKeymaps().associateBy { it.name }
        val remoteKeymaps = ohMyJson.decodeFromString<List<JsonObject>>(text).mapNotNull { Keymap.fromJSON(it) }
        for (keymap in remoteKeymaps) {
            val localKeymap = localKeymaps[keymap.name]
            if (localKeymap != null) {
                if (localKeymap.updateDate > keymap.updateDate) {
                    continue
                }
            }
            keymapManager.addKeymap(keymap)
        }

        SwingUtilities.invokeLater {
            deletedData.forEach {
                keymapManager.removeKeymap(it.id)
                deleteDataManager.removeKeymap(it.id, it.deleteDate)
            }
        }

        if (log.isDebugEnabled) {
            log.debug("Decode Keymaps: {}", text)
        }
    }

    protected fun encodeKeymaps(): String {
        val keymaps = mutableListOf<JsonObject>()
        for (keymap in keymapManager.getKeymaps()) {
            // 只读的是内置的
            if (keymap.isReadonly) {
                continue
            }
            keymaps.add(keymap.toJSONObject())
        }

        return ohMyJson.encodeToString(keymaps)
    }

    protected open fun getKey(config: SyncConfig): ByteArray {
        return ArrayUtils.subarray(config.token.padEnd(16, '0').toByteArray(), 0, 16)
    }

    protected fun getIv(id: String): ByteArray {
        return ArrayUtils.subarray(id.padEnd(16, '0').toByteArray(), 0, 16)
    }
}