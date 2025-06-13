package app.termora.plugins.sync

import app.termora.*
import app.termora.AES.encodeBase64String
import app.termora.Application.ohMyJson
import app.termora.account.AccountManager
import app.termora.account.AccountOwner
import app.termora.database.DatabaseManager
import app.termora.database.OwnerType
import app.termora.highlight.KeywordHighlight
import app.termora.highlight.KeywordHighlightManager
import app.termora.keymap.Keymap
import app.termora.keymap.KeymapManager
import app.termora.keymgr.KeyManager
import app.termora.keymgr.OhKeyPair
import app.termora.macro.Macro
import app.termora.macro.MacroManager
import app.termora.nv.FileChooser
import app.termora.snippet.Snippet
import app.termora.snippet.SnippetManager
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatComboBox
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.commons.lang3.time.DateFormatUtils
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.ItemEvent
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.function.Consumer
import javax.swing.*
import javax.swing.event.DocumentEvent

class CloudSyncOption : JPanel(BorderLayout()), OptionsPane.PluginOption {

    companion object {
        private val log = LoggerFactory.getLogger(CloudSyncOption::class.java)
    }

    private val database get() = DatabaseManager.getInstance()
    private val hostManager get() = HostManager.getInstance()
    private val snippetManager get() = SnippetManager.getInstance()
    private val keymapManager get() = KeymapManager.getInstance()
    private val macroManager get() = MacroManager.getInstance()
    private val keywordHighlightManager get() = KeywordHighlightManager.getInstance()
    private val keyManager get() = KeyManager.getInstance()
    private val formMargin = "7dlu"
    private val accountManager get() = AccountManager.getInstance()
    private val accountOwner
        get() = AccountOwner(
            id = accountManager.getAccountId(),
            name = accountManager.getEmail(),
            type = OwnerType.User
        )

    val typeComboBox = FlatComboBox<SyncType>()
    val tokenTextField = OutlinePasswordField(255)
    val gistTextField = OutlineTextField(255)
    val policyComboBox = JComboBox<SyncPolicy>()
    val domainTextField = OutlineTextField(255)
    val syncConfigButton = JButton(I18n.getString("termora.settings.sync"), Icons.settingSync)
    val exportConfigButton = JButton(I18n.getString("termora.settings.sync.export"), Icons.export)
    val importConfigButton = JButton(I18n.getString("termora.settings.sync.import"), Icons.import)
    val lastSyncTimeLabel = JLabel()
    val sync get() = SyncProperties.getInstance()
    val hostsCheckBox = JCheckBox(I18n.getString("termora.welcome.my-hosts"))
    val keysCheckBox = JCheckBox(I18n.getString("termora.settings.sync.range.keys"))
    val snippetsCheckBox = JCheckBox(I18n.getString("termora.snippet.title"))
    val keywordHighlightsCheckBox = JCheckBox(I18n.getString("termora.settings.sync.range.keyword-highlights"))
    val macrosCheckBox = JCheckBox(I18n.getString("termora.macro"))
    val keymapCheckBox = JCheckBox(I18n.getString("termora.settings.keymap"))
    val visitGistBtn = JButton(Icons.externalLink)
    val getTokenBtn = JButton(Icons.externalLink)
    private val owner get() = SwingUtilities.getWindowAncestor(this)

    init {
        initView()
        initEvents()
        add(getCenterComponent(), BorderLayout.CENTER)
    }

    private fun initEvents() {
        syncConfigButton.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (typeComboBox.selectedItem == SyncType.WebDAV) {
                    if (tokenTextField.password.isEmpty()) {
                        tokenTextField.outline = FlatClientProperties.OUTLINE_ERROR
                        tokenTextField.requestFocusInWindow()
                        return
                    } else if (gistTextField.text.isEmpty()) {
                        gistTextField.outline = FlatClientProperties.OUTLINE_ERROR
                        gistTextField.requestFocusInWindow()
                        return
                    }
                }
                swingCoroutineScope.launch(Dispatchers.IO) { sync() }
            }
        })

        typeComboBox.addItemListener {
            if (it.stateChange == ItemEvent.SELECTED) {
                sync.type = typeComboBox.selectedItem as SyncType

                if (typeComboBox.selectedItem == SyncType.GitLab) {
                    if (domainTextField.text.isBlank()) {
                        domainTextField.text = StringUtils.defaultIfBlank(sync.domain, "https://gitlab.com/api")
                    }
                }

                removeAll()
                add(getCenterComponent(), BorderLayout.CENTER)
                revalidate()
                repaint()
            }
        }

        policyComboBox.addItemListener {
            if (it.stateChange == ItemEvent.SELECTED) {
                sync.policy = (policyComboBox.selectedItem as SyncPolicy).name
            }
        }

        tokenTextField.document.addDocumentListener(object : DocumentAdaptor() {
            override fun changedUpdate(e: DocumentEvent) {
                sync.token = String(tokenTextField.password)
                tokenTextField.trailingComponent = if (tokenTextField.password.isEmpty()) getTokenBtn else null
            }
        })

        domainTextField.document.addDocumentListener(object : DocumentAdaptor() {
            override fun changedUpdate(e: DocumentEvent) {
                sync.domain = domainTextField.text
            }
        })

        gistTextField.document.addDocumentListener(object : DocumentAdaptor() {
            override fun changedUpdate(e: DocumentEvent) {
                sync.gist = gistTextField.text
                gistTextField.trailingComponent = if (gistTextField.text.isNotBlank()) visitGistBtn else null
            }
        })


        visitGistBtn.addActionListener {
            if (typeComboBox.selectedItem == SyncType.GitLab) {
                if (domainTextField.text.isNotBlank()) {
                    try {
                        val baseUrl = URI.create(domainTextField.text)
                        val url = StringBuilder()
                        url.append(baseUrl.scheme).append("://")
                        url.append(baseUrl.host)
                        if (baseUrl.port > 0) {
                            url.append(":").append(baseUrl.port)
                        }
                        url.append("/-/snippets/").append(gistTextField.text)
                        Application.browse(URI.create(url.toString()))
                    } catch (e: Exception) {
                        if (log.isErrorEnabled) {
                            log.error(e.message, e)
                        }
                    }
                }
            } else if (typeComboBox.selectedItem == SyncType.GitHub) {
                Application.browse(URI.create("https://gist.github.com/${gistTextField.text}"))
            }
        }

        getTokenBtn.addActionListener {
            when (typeComboBox.selectedItem) {
                SyncType.GitLab -> {
                    val uri = URI.create(domainTextField.text)
                    Application.browse(URI.create("${uri.scheme}://${uri.host}/-/user_settings/personal_access_tokens?name=Termora%20Sync%20Config&scopes=api"))
                }

                SyncType.GitHub -> Application.browse(URI.create("https://github.com/settings/tokens"))
                SyncType.Gitee -> Application.browse(URI.create("https://gitee.com/profile/personal_access_tokens"))
            }
        }

        exportConfigButton.addActionListener { export() }
        importConfigButton.addActionListener { import() }

        keysCheckBox.addActionListener { refreshButtons() }
        hostsCheckBox.addActionListener { refreshButtons() }
        snippetsCheckBox.addActionListener { refreshButtons() }
        keywordHighlightsCheckBox.addActionListener { refreshButtons() }

    }

    private suspend fun sync() {

        // 如果 gist 为空说明要创建一个 gist
        if (gistTextField.text.isBlank()) {
            if (!pushOrPull(true)) return
        } else {
            if (!pushOrPull(false)) return
            if (!pushOrPull(true)) return
        }

        withContext(Dispatchers.Swing) {
            OptionPane.showMessageDialog(owner, message = I18n.getString("termora.settings.sync.done"))
        }
    }

    private fun visit(c: JComponent, consumer: Consumer<JComponent>) {
        for (e in c.components) {
            if (e is JComponent) {
                consumer.accept(e)
                visit(e, consumer)
            }
        }
    }

    private fun refreshButtons() {
        sync.rangeKeyPairs = keysCheckBox.isSelected
        sync.rangeHosts = hostsCheckBox.isSelected
        sync.rangeSnippets = snippetsCheckBox.isSelected
        sync.rangeKeywordHighlights = keywordHighlightsCheckBox.isSelected

        syncConfigButton.isEnabled = keysCheckBox.isSelected || hostsCheckBox.isSelected
                || keywordHighlightsCheckBox.isSelected
        exportConfigButton.isEnabled = syncConfigButton.isEnabled
        importConfigButton.isEnabled = syncConfigButton.isEnabled
    }

    private fun export() {

        assertEventDispatchThread()

        val passwordField = OutlinePasswordField()
        val panel = object : JPanel(BorderLayout()) {
            override fun requestFocusInWindow(): Boolean {
                return passwordField.requestFocusInWindow()
            }
        }

        val label = JLabel(I18n.getString("termora.settings.sync.export-encrypt") + StringUtils.SPACE.repeat(25))
        label.border = BorderFactory.createEmptyBorder(0, 0, 8, 0)
        panel.add(label, BorderLayout.NORTH)
        panel.add(passwordField, BorderLayout.CENTER)

        var password = StringUtils.EMPTY

        if (OptionPane.showConfirmDialog(
                owner,
                panel,
                optionType = JOptionPane.YES_NO_OPTION,
                initialValue = passwordField
            ) == JOptionPane.YES_OPTION
        ) {
            password = String(passwordField.password).trim()
        }


        val fileChooser = FileChooser()
        fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
        fileChooser.win32Filters.add(Pair("All Files", listOf("*")))
        fileChooser.win32Filters.add(Pair("JSON files", listOf("json")))
        fileChooser.showSaveDialog(owner, "${Application.getName()}.json").thenAccept { file ->
            if (file != null) {
                SwingUtilities.invokeLater { exportText(file, password) }
            }
        }
    }

    private fun import() {
        val fileChooser = FileChooser()
        fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
        fileChooser.osxAllowedFileTypes = listOf("json")
        fileChooser.win32Filters.add(Pair("JSON files", listOf("json")))
        fileChooser.showOpenDialog(owner).thenAccept { files ->
            if (files.isNotEmpty()) {
                SwingUtilities.invokeLater { importFromFile(files.first()) }
            }
        }
    }

    @Suppress("DuplicatedCode")
    private fun importFromFile(file: File) {
        if (!file.exists()) {
            return
        }

        val ranges = getSyncConfig().ranges
        if (ranges.isEmpty()) {
            return
        }

        // 最大 100MB
        if (file.length() >= 1024 * 1024 * 100) {
            OptionPane.showMessageDialog(
                owner, I18n.getString("termora.settings.sync.import.file-too-large"),
                messageType = JOptionPane.ERROR_MESSAGE
            )
            return
        }

        val text = file.readText()
        val jsonResult = ohMyJson.runCatching { decodeFromString<JsonObject>(text) }
        if (jsonResult.isFailure) {
            val e = jsonResult.exceptionOrNull() ?: return
            OptionPane.showMessageDialog(
                owner, ExceptionUtils.getRootCauseMessage(e),
                messageType = JOptionPane.ERROR_MESSAGE
            )
            return
        }

        var json = jsonResult.getOrNull() ?: return

        // 如果加密了 则解密数据
        if (json["encryption"]?.jsonPrimitive?.booleanOrNull == true) {
            val data = json["data"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
            if (data.isBlank()) {
                OptionPane.showMessageDialog(
                    owner, "Data file corruption",
                    messageType = JOptionPane.ERROR_MESSAGE
                )
                return
            }

            while (true) {
                val passwordField = OutlinePasswordField()
                val panel = object : JPanel(BorderLayout()) {
                    override fun requestFocusInWindow(): Boolean {
                        return passwordField.requestFocusInWindow()
                    }
                }

                val label = JLabel("Please enter the password" + StringUtils.SPACE.repeat(25))
                label.border = BorderFactory.createEmptyBorder(0, 0, 8, 0)
                panel.add(label, BorderLayout.NORTH)
                panel.add(passwordField, BorderLayout.CENTER)

                if (OptionPane.showConfirmDialog(
                        owner,
                        panel,
                        optionType = JOptionPane.YES_NO_OPTION,
                        initialValue = passwordField
                    ) != JOptionPane.YES_OPTION
                ) {
                    return
                }

                if (passwordField.password.isEmpty()) {
                    OptionPane.showMessageDialog(
                        owner, I18n.getString("termora.doorman.unlock-data"),
                        messageType = JOptionPane.ERROR_MESSAGE
                    )
                    continue
                }

                val password = String(passwordField.password)
                val key = PBKDF2.generateSecret(
                    password.toCharArray(),
                    password.toByteArray(), keyLength = 128
                )

                try {
                    val dataText = AES.ECB.decrypt(key, Base64.decodeBase64(data)).toString(Charsets.UTF_8)
                    val dataJsonResult = ohMyJson.runCatching { decodeFromString<JsonObject>(dataText) }
                    if (dataJsonResult.isFailure) {
                        val e = dataJsonResult.exceptionOrNull() ?: return
                        OptionPane.showMessageDialog(
                            owner, ExceptionUtils.getRootCauseMessage(e),
                            messageType = JOptionPane.ERROR_MESSAGE
                        )
                        return
                    }
                    json = dataJsonResult.getOrNull() ?: return
                    break
                } catch (_: Exception) {
                    OptionPane.showMessageDialog(
                        owner, I18n.getString("termora.doorman.password-wrong"),
                        messageType = JOptionPane.ERROR_MESSAGE
                    )
                }

            }
        }

        if (ranges.contains(SyncRange.Hosts)) {
            val hosts = json["hosts"]
            if (hosts is JsonArray) {
                ohMyJson.runCatching { decodeFromJsonElement<List<Host>>(hosts.jsonArray) }.onSuccess {
                    for (host in it) {
                        hostManager.addHost(host)
                    }
                }
            }
        }

        if (ranges.contains(SyncRange.Snippets)) {
            val snippets = json["snippets"]
            if (snippets is JsonArray) {
                ohMyJson.runCatching { decodeFromJsonElement<List<Snippet>>(snippets.jsonArray) }.onSuccess {
                    for (snippet in it) {
                        snippetManager.addSnippet(snippet)
                    }
                }
            }
        }

        if (ranges.contains(SyncRange.KeyPairs)) {
            val keyPairs = json["keyPairs"]
            if (keyPairs is JsonArray) {
                ohMyJson.runCatching { decodeFromJsonElement<List<OhKeyPair>>(keyPairs.jsonArray) }.onSuccess {
                    for (keyPair in it) {
                        keyManager.addOhKeyPair(keyPair, accountOwner)
                    }
                }
            }
        }

        if (ranges.contains(SyncRange.KeywordHighlights)) {
            val keywordHighlights = json["keywordHighlights"]
            if (keywordHighlights is JsonArray) {
                ohMyJson.runCatching { decodeFromJsonElement<List<KeywordHighlight>>(keywordHighlights.jsonArray) }
                    .onSuccess {
                        for (keyPair in it) {
                            keywordHighlightManager.addKeywordHighlight(keyPair, accountOwner)
                        }
                    }
            }
        }

        if (ranges.contains(SyncRange.Macros)) {
            val macros = json["macros"]
            if (macros is JsonArray) {
                ohMyJson.runCatching { decodeFromJsonElement<List<Macro>>(macros.jsonArray) }.onSuccess {
                    for (macro in it) {
                        macroManager.addMacro(macro)
                    }
                }
            }
        }

        if (ranges.contains(SyncRange.Keymap)) {
            val keymaps = json["keymaps"]
            if (keymaps is JsonArray) {
                for (keymap in keymaps.jsonArray.mapNotNull { Keymap.fromJSON(it.jsonObject) }) {
                    keymapManager.addKeymap(keymap)
                }
            }
        }

        OptionPane.showMessageDialog(
            owner, I18n.getString("termora.settings.sync.import.successful"),
            messageType = JOptionPane.INFORMATION_MESSAGE
        )
    }

    private fun exportText(file: File, password: String) {
        val syncConfig = getSyncConfig()
        var text = ohMyJson.encodeToString(buildJsonObject {
            val now = System.currentTimeMillis()
            put("exporter", SystemUtils.USER_NAME)
            put("version", Application.getVersion())
            put("exportDate", now)
            put("os", SystemUtils.OS_NAME)
            put("exportDateHuman", DateFormatUtils.ISO_8601_EXTENDED_DATETIME_TIME_ZONE_FORMAT.format(Date(now)))
            if (syncConfig.ranges.contains(SyncRange.Hosts)) {
                put("hosts", ohMyJson.encodeToJsonElement(hostManager.hosts()))
            }
            if (syncConfig.ranges.contains(SyncRange.Snippets)) {
                put("snippets", ohMyJson.encodeToJsonElement(snippetManager.snippets()))
            }
            if (syncConfig.ranges.contains(SyncRange.KeyPairs)) {
                put("keyPairs", ohMyJson.encodeToJsonElement(keyManager.getOhKeyPairs()))
            }
            if (syncConfig.ranges.contains(SyncRange.KeywordHighlights)) {
                put(
                    "keywordHighlights",
                    ohMyJson.encodeToJsonElement(keywordHighlightManager.getKeywordHighlights())
                )
            }
            if (syncConfig.ranges.contains(SyncRange.Macros)) {
                put(
                    "macros",
                    ohMyJson.encodeToJsonElement(macroManager.getMacros())
                )
            }
            if (syncConfig.ranges.contains(SyncRange.Keymap)) {
                val keymaps = keymapManager.getKeymaps().filter { !it.isReadonly }
                    .map { it.toJSONObject() }
                put(
                    "keymaps",
                    ohMyJson.encodeToJsonElement(keymaps)
                )
            }
            put("settings", buildJsonObject {
                put("appearance", ohMyJson.encodeToJsonElement(database.appearance.getProperties()))
                put("sync", ohMyJson.encodeToJsonElement(sync.getProperties()))
                put("terminal", ohMyJson.encodeToJsonElement(database.terminal.getProperties()))
            })
        })

        if (password.isNotBlank()) {
            val key = PBKDF2.generateSecret(
                password.toCharArray(),
                password.toByteArray(), keyLength = 128
            )

            text = ohMyJson.encodeToString(buildJsonObject {
                put("encryption", true)
                put("data", AES.ECB.encrypt(key, text.toByteArray(Charsets.UTF_8)).encodeBase64String())
            })
        }

        file.outputStream().use {
            IOUtils.write(text, it, StandardCharsets.UTF_8)
            OptionPane.openFileInFolder(
                owner,
                file, I18n.getString("termora.settings.sync.export-done-open-folder"),
                I18n.getString("termora.settings.sync.export-done")
            )
        }
    }

    private fun getSyncConfig(): SyncConfig {
        val range = mutableSetOf<SyncRange>()
        if (hostsCheckBox.isSelected) {
            range.add(SyncRange.Hosts)
        }
        if (keysCheckBox.isSelected) {
            range.add(SyncRange.KeyPairs)
        }
        if (keywordHighlightsCheckBox.isSelected) {
            range.add(SyncRange.KeywordHighlights)
        }
        if (macrosCheckBox.isSelected) {
            range.add(SyncRange.Macros)
        }
        if (keymapCheckBox.isSelected) {
            range.add(SyncRange.Keymap)
        }
        if (snippetsCheckBox.isSelected) {
            range.add(SyncRange.Snippets)
        }
        return SyncConfig(
            type = typeComboBox.selectedItem as SyncType,
            token = String(tokenTextField.password),
            gistId = gistTextField.text,
            options = mapOf("domain" to domainTextField.text),
            ranges = range
        )
    }

    /**
     * @return true 同步成功
     */
    @Suppress("DuplicatedCode")
    private suspend fun pushOrPull(push: Boolean): Boolean {

        if (typeComboBox.selectedItem == SyncType.GitLab) {
            if (domainTextField.text.isBlank()) {
                withContext(Dispatchers.Swing) {
                    domainTextField.outline = "error"
                    domainTextField.requestFocusInWindow()
                }
                return false
            }
        }

        if (tokenTextField.password.isEmpty()) {
            withContext(Dispatchers.Swing) {
                tokenTextField.outline = "error"
                tokenTextField.requestFocusInWindow()
            }
            return false
        }

        if (gistTextField.text.isBlank() && !push) {
            withContext(Dispatchers.Swing) {
                gistTextField.outline = "error"
                gistTextField.requestFocusInWindow()
            }
            return false
        }

        withContext(Dispatchers.Swing) {
            exportConfigButton.isEnabled = false
            importConfigButton.isEnabled = false
            syncConfigButton.isEnabled = false
            typeComboBox.isEnabled = false
            gistTextField.isEnabled = false
            tokenTextField.isEnabled = false
            keysCheckBox.isEnabled = false
            macrosCheckBox.isEnabled = false
            keymapCheckBox.isEnabled = false
            keywordHighlightsCheckBox.isEnabled = false
            hostsCheckBox.isEnabled = false
            snippetsCheckBox.isEnabled = false
            domainTextField.isEnabled = false
            syncConfigButton.text = "${I18n.getString("termora.settings.sync")}..."
        }

        val syncConfig = getSyncConfig()

        // sync
        val syncResult = runCatching {
            val syncer = SyncManager.getInstance()
            if (push) {
                syncer.push(syncConfig)
            } else {
                syncer.pull(syncConfig)
            }
        }

        // 恢复状态
        withContext(Dispatchers.Swing) {
            syncConfigButton.isEnabled = true
            exportConfigButton.isEnabled = true
            importConfigButton.isEnabled = true
            keysCheckBox.isEnabled = true
            hostsCheckBox.isEnabled = true
            snippetsCheckBox.isEnabled = true
            typeComboBox.isEnabled = true
            macrosCheckBox.isEnabled = true
            keymapCheckBox.isEnabled = true
            gistTextField.isEnabled = true
            tokenTextField.isEnabled = true
            domainTextField.isEnabled = true
            keywordHighlightsCheckBox.isEnabled = true
            syncConfigButton.text = I18n.getString("termora.settings.sync")
        }

        // 如果失败，提示错误
        if (syncResult.isFailure) {
            val exception = syncResult.exceptionOrNull()
            var message = exception?.message ?: "Failed to sync data"
            if (exception is ResponseException) {
                message = "Server response: ${exception.code}"
            }

            if (exception != null) {
                if (log.isErrorEnabled) {
                    log.error(exception.message, exception)
                }
            }

            withContext(Dispatchers.Swing) {
                OptionPane.showMessageDialog(owner, message, messageType = JOptionPane.ERROR_MESSAGE)
            }

        } else {
            withContext(Dispatchers.Swing) {
                val now = System.currentTimeMillis()
                sync.lastSyncTime = now
                val date = DateFormatUtils.format(Date(now), I18n.getString("termora.date-format"))
                lastSyncTimeLabel.text = "${I18n.getString("termora.settings.sync.last-sync-time")}: $date"
                if (push && gistTextField.text.isBlank()) {
                    gistTextField.text = syncResult.map { it.config }.getOrDefault(syncConfig).gistId
                }
            }
        }

        return syncResult.isSuccess

    }

    private fun initView() {
        typeComboBox.addItem(SyncType.GitHub)
        typeComboBox.addItem(SyncType.GitLab)
        typeComboBox.addItem(SyncType.Gitee)
        typeComboBox.addItem(SyncType.WebDAV)

        policyComboBox.addItem(SyncPolicy.Manual)
        policyComboBox.addItem(SyncPolicy.OnChange)

        hostsCheckBox.isFocusable = false
        snippetsCheckBox.isFocusable = false
        keysCheckBox.isFocusable = false
        keywordHighlightsCheckBox.isFocusable = false
        macrosCheckBox.isFocusable = false
        keymapCheckBox.isFocusable = false

        hostsCheckBox.isSelected = sync.rangeHosts
        snippetsCheckBox.isSelected = sync.rangeSnippets
        keysCheckBox.isSelected = sync.rangeKeyPairs
        keywordHighlightsCheckBox.isSelected = sync.rangeKeywordHighlights
        macrosCheckBox.isSelected = sync.rangeMacros
        keymapCheckBox.isSelected = sync.rangeKeymap

        if (sync.policy == SyncPolicy.Manual.name) {
            policyComboBox.selectedItem = SyncPolicy.Manual
        } else if (sync.policy == SyncPolicy.OnChange.name) {
            policyComboBox.selectedItem = SyncPolicy.OnChange
        }

        typeComboBox.selectedItem = sync.type
        gistTextField.text = sync.gist
        tokenTextField.text = sync.token
        domainTextField.trailingComponent = JButton(Icons.externalLink).apply {
            addActionListener {
                if (typeComboBox.selectedItem == SyncType.GitLab) {
                    Application.browse(URI.create("https://docs.gitlab.com/ee/api/snippets.html"))

                } else if (typeComboBox.selectedItem == SyncType.WebDAV) {
                    val url = domainTextField.text
                    if (url.isNullOrBlank()) {
                        OptionPane.showMessageDialog(
                            owner,
                            I18n.getString("termora.settings.sync.webdav.help")
                        )
                    } else {
                        val uri = URI.create(url)
                        val sb = StringBuilder()
                        sb.append(uri.scheme).append("://")
                        if (tokenTextField.password.isNotEmpty() && gistTextField.text.isNotBlank()) {
                            sb.append(String(tokenTextField.password)).append(":").append(gistTextField.text)
                            sb.append('@')
                        }
                        sb.append(uri.authority).append(uri.path)
                        if (!uri.query.isNullOrBlank()) {
                            sb.append('?').append(uri.query)
                        }
                        Application.browse(URI.create(sb.toString()))
                    }
                }
            }
        }

        if (typeComboBox.selectedItem != SyncType.Gitee) {
            gistTextField.trailingComponent = if (gistTextField.text.isNotBlank()) visitGistBtn else null
        }

        tokenTextField.trailingComponent = if (tokenTextField.password.isEmpty()) getTokenBtn else null

        if (domainTextField.text.isBlank()) {
            if (typeComboBox.selectedItem == SyncType.GitLab) {
                domainTextField.text = StringUtils.defaultIfBlank(sync.domain, "https://gitlab.com/api")
            } else if (typeComboBox.selectedItem == SyncType.WebDAV) {
                domainTextField.text = sync.domain
            }
        }

        policyComboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                var text = value?.toString() ?: StringUtils.EMPTY
                if (value == SyncPolicy.Manual) {
                    text = I18n.getString("termora.settings.sync.policy.manual")
                } else if (value == SyncPolicy.OnChange) {
                    text = I18n.getString("termora.settings.sync.policy.on-change")
                }
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
            }
        }

        val lastSyncTime = sync.lastSyncTime
        lastSyncTimeLabel.text = "${I18n.getString("termora.settings.sync.last-sync-time")}: ${
            if (lastSyncTime > 0) DateFormatUtils.format(
                Date(lastSyncTime), I18n.getString("termora.date-format")
            ) else "-"
        }"

        refreshButtons()


    }

    override fun getIcon(isSelected: Boolean): Icon {
        return Icons.cloud
    }

    override fun getTitle(): String {
        return I18n.getString("termora.settings.sync")
    }

    override fun getJComponent(): JComponent {
        return this
    }

    private fun getCenterComponent(): JComponent {
        val layout = FormLayout(
            "left:pref, $formMargin, default:grow, 30dlu",
            "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
        )

        val rangeBox = FormBuilder.create()
            .layout(
                FormLayout(
                    "left:pref, $formMargin, left:pref, $formMargin, left:pref",
                    "pref, 2dlu, pref"
                )
            )
            .add(hostsCheckBox).xy(1, 1)
            .add(keysCheckBox).xy(3, 1)
            .add(keywordHighlightsCheckBox).xy(5, 1)
            .add(macrosCheckBox).xy(1, 3)
            .add(keymapCheckBox).xy(3, 3)
            .add(snippetsCheckBox).xy(5, 3)
            .build()

        var rows = 1
        val step = 2
        val builder = FormBuilder.create().layout(layout).debug(false)
        val box = Box.createHorizontalBox()
        box.add(typeComboBox)
        if (typeComboBox.selectedItem == SyncType.GitLab || typeComboBox.selectedItem == SyncType.WebDAV) {
            box.add(Box.createHorizontalStrut(4))
            box.add(domainTextField)
        }
        builder.add("${I18n.getString("termora.settings.sync.type")}:").xy(1, rows)
            .add(box).xy(3, rows).apply { rows += step }

        val isWebDAV = typeComboBox.selectedItem == SyncType.WebDAV

        val tokenText = if (isWebDAV) {
            I18n.getString("termora.new-host.general.username")
        } else {
            I18n.getString("termora.settings.sync.token")
        }

        val gistText = if (isWebDAV) {
            I18n.getString("termora.new-host.general.password")
        } else {
            I18n.getString("termora.settings.sync.gist")
        }

        if (typeComboBox.selectedItem == SyncType.Gitee || isWebDAV) {
            gistTextField.trailingComponent = null
        } else {
            gistTextField.trailingComponent = visitGistBtn
        }

        val syncPolicyBox = Box.createHorizontalBox()
        syncPolicyBox.add(policyComboBox)
        syncPolicyBox.add(Box.createHorizontalGlue())
        syncPolicyBox.add(Box.createHorizontalGlue())

        builder.add("${tokenText}:").xy(1, rows)
            .add(if (isWebDAV) gistTextField else tokenTextField).xy(3, rows).apply { rows += step }
            .add("${gistText}:").xy(1, rows)
            .add(if (isWebDAV) tokenTextField else gistTextField).xy(3, rows).apply { rows += step }
            .add("${I18n.getString("termora.settings.sync.policy")}:").xy(1, rows)
            .add(syncPolicyBox).xy(3, rows).apply { rows += step }
            .add("${I18n.getString("termora.settings.sync.range")}:").xy(1, rows)
            .add(rangeBox).xy(3, rows).apply { rows += step }
            // Sync buttons
            .add(
                FormBuilder.create()
                    .layout(FormLayout("pref, 2dlu, pref, 2dlu, pref", "pref"))
                    .add(syncConfigButton).xy(1, 1)
                    .add(exportConfigButton).xy(3, 1)
                    .add(importConfigButton).xy(5, 1)
                    .build()
            ).xy(3, rows, "center, fill").apply { rows += step }
            .add(lastSyncTimeLabel).xy(3, rows, "center, fill").apply { rows += step }


        return builder.build()

    }
}