package app.termora

import app.termora.AES.encodeBase64String
import app.termora.Application.ohMyJson
import app.termora.db.Database
import app.termora.highlight.KeywordHighlightManager
import app.termora.keymgr.KeyManager
import app.termora.macro.MacroManager
import app.termora.native.FileChooser
import app.termora.sync.SyncConfig
import app.termora.sync.SyncRange
import app.termora.sync.SyncType
import app.termora.sync.SyncerProvider
import app.termora.terminal.CursorStyle
import app.termora.terminal.DataKey
import app.termora.terminal.panel.TerminalPanel
import app.termora.localshell.LocalShell
import app.termora.localshell.LocalShellDetect
import app.termora.localshell.LocalShellProvider
import cash.z.ecc.android.bip39.Mnemonics
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatButton
import com.formdev.flatlaf.extras.components.FlatComboBox
import com.formdev.flatlaf.extras.components.FlatLabel
import com.formdev.flatlaf.util.SystemInfo
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import com.jthemedetecor.OsThemeDetector
import com.sun.jna.LastErrorException
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.lang3.time.DateFormatUtils
import org.jdesktop.swingx.JXEditorPane
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.ItemEvent
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import kotlin.time.Duration.Companion.milliseconds


class SettingsOptionsPane : OptionsPane() {
    private val owner get() = SwingUtilities.getWindowAncestor(this@SettingsOptionsPane)
    private val database get() = Database.instance

    companion object {
        private val log = LoggerFactory.getLogger(SettingsOptionsPane::class.java)
        private val localShells by lazy { loadShells() }
        var pulled = false

        private fun loadShells(): Collection<LocalShell> {
            return LocalShellDetect.getSupportAllLocalShell()
        }


    }

    init {
        addOption(AppearanceOption())
        addOption(TerminalOption())
        addOption(CloudSyncOption())
        addOption(DoormanOption())
        addOption(AboutOption())
        setContentBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8))
    }

    private inner class AppearanceOption : JPanel(BorderLayout()), Option {
        val themeManager = ThemeManager.instance
        val themeComboBox = FlatComboBox<String>()
        val languageComboBox = FlatComboBox<String>()
        val followSystemCheckBox = JCheckBox(I18n.getString("termora.settings.appearance.follow-system"))
        private val appearance get() = database.appearance

        init {
            initView()
            initEvents()
        }

        private fun initView() {

            followSystemCheckBox.isSelected = appearance.followSystem

            themeComboBox.isEnabled = !followSystemCheckBox.isSelected
            themeManager.themes.keys.forEach { themeComboBox.addItem(it) }
            themeComboBox.selectedItem = themeManager.theme

            I18n.getLanguages().forEach { languageComboBox.addItem(it.key) }
            languageComboBox.selectedItem = appearance.language
            languageComboBox.renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    return super.getListCellRendererComponent(
                        list,
                        I18n.getLanguages().getValue(value as String),
                        index,
                        isSelected,
                        cellHasFocus
                    )
                }
            }

            add(getFormPanel(), BorderLayout.CENTER)
        }

        private fun initEvents() {
            themeComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    appearance.theme = themeComboBox.selectedItem as String
                    SwingUtilities.invokeLater { themeManager.change(themeComboBox.selectedItem as String) }
                }
            }

            followSystemCheckBox.addActionListener {
                appearance.followSystem = followSystemCheckBox.isSelected
                themeComboBox.isEnabled = !followSystemCheckBox.isSelected

                if (followSystemCheckBox.isSelected) {
                    SwingUtilities.invokeLater {
                        if (OsThemeDetector.getDetector().isDark) {
                            if (!FlatLaf.isLafDark()) {
                                themeManager.change("Dark")
                                themeComboBox.selectedItem = "Dark"
                            }
                        } else {
                            if (FlatLaf.isLafDark()) {
                                themeManager.change("Light")
                                themeComboBox.selectedItem = "Light"
                            }
                        }
                    }
                }
            }

            languageComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    appearance.language = languageComboBox.selectedItem as String
                    SwingUtilities.invokeLater {
                        OptionPane.showMessageDialog(
                            owner,
                            I18n.getString("termora.settings.restart.message"),
                            I18n.getString("termora.settings.restart.title"),
                            messageType = JOptionPane.INFORMATION_MESSAGE,
                        )
                    }
                }
            }
        }

        override fun getIcon(isSelected: Boolean): Icon {
            return Icons.uiForm
        }

        override fun getTitle(): String {
            return I18n.getString("termora.settings.appearance")
        }

        override fun getJComponent(): JComponent {
            return this
        }


        private fun getFormPanel(): JPanel {
            val layout = FormLayout(
                "left:pref, $formMargin, default:grow, $formMargin, default, default:grow",
                "pref, $formMargin, pref, $formMargin"
            )

            var rows = 1
            val step = 2
            return FormBuilder.create().layout(layout)
                .add("${I18n.getString("termora.settings.appearance.theme")}:").xy(1, rows)
                .add(themeComboBox).xy(3, rows)
                .add(followSystemCheckBox).xy(5, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.appearance.language")}:").xy(1, rows)
                .add(languageComboBox).xy(3, rows)
                .add(Hyperlink(object : AnAction(I18n.getString("termora.settings.appearance.i-want-to-translate")) {
                    override fun actionPerformed(evt: ActionEvent) {
                        Application.browse(URI.create("https://github.com/TermoraDev/termora/tree/main/src/main/resources/i18n"))
                    }
                })).xy(5, rows).apply { rows += step }
                .build()
        }


    }

    private inner class TerminalOption : JPanel(BorderLayout()), Option {
        private val cursorStyleComboBox = FlatComboBox<CursorStyle>()
        private val debugComboBox = YesOrNoComboBox()
        private val fontComboBox = FlatComboBox<String>()
        private val shellComboBox = FlatComboBox<LocalShellProvider>()
        private val maxRowsTextField = IntSpinner(0, 0)
        private val fontSizeTextField = IntSpinner(0, 9, 99)
        private val terminalSetting get() = Database.instance.terminal
        private val selectCopyComboBox = YesOrNoComboBox()

        init {
            initView()
            initEvents()
            add(getCenterComponent(), BorderLayout.CENTER)
        }

        private fun initEvents() {
            fontComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    terminalSetting.font = fontComboBox.selectedItem as String
                    fireFontChanged()
                }
            }

            selectCopyComboBox.addItemListener { e ->
                if (e.stateChange == ItemEvent.SELECTED) {
                    terminalSetting.selectCopy = selectCopyComboBox.selectedItem as Boolean
                }
            }

            fontSizeTextField.addChangeListener {
                terminalSetting.fontSize = fontSizeTextField.value as Int
                fireFontChanged()
            }

            maxRowsTextField.addChangeListener {
                terminalSetting.maxRows = maxRowsTextField.value as Int
            }

            cursorStyleComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    val style = cursorStyleComboBox.selectedItem as CursorStyle
                    terminalSetting.cursor = style
                    TerminalFactory.instance.getTerminals().forEach { e ->
                        e.getTerminalModel().setData(DataKey.CursorStyle, style)
                    }
                }
            }


            debugComboBox.addItemListener { e ->
                if (e.stateChange == ItemEvent.SELECTED) {
                    terminalSetting.debug = debugComboBox.selectedItem as Boolean
                    TerminalFactory.instance.getTerminals().forEach {
                        it.getTerminalModel().setData(TerminalPanel.Debug, terminalSetting.debug)
                    }
                }
            }


            shellComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    when (shellComboBox.selectedItem) {
                        LocalShellProvider.EmptyCustomCommandLineArgs -> {
                            // 选择了自定义命令行
                            shellComboBox.isEditable = true
                            shellComboBox.selectedItem = ""
                        }

                        is String -> {
                            // 手动输入命令的
                            terminalSetting.localShell = LocalShellProvider.CustomCommandLineArgs(CommandLineArgs.of(shellComboBox.selectedItem as String)).localShell.id.asIdentificationString()
                        }

                        is LocalShellProvider.Specify -> {
                            shellComboBox.isEditable = false
                            terminalSetting.localShell = (shellComboBox.selectedItem as LocalShellProvider.Specify).localShell.id.asIdentificationString()
                        }
                    }
                }
            }

        }

        private fun fireFontChanged() {
            TerminalPanelFactory.instance.fireResize()
        }

        private fun initView() {

            fontSizeTextField.value = terminalSetting.fontSize
            maxRowsTextField.value = terminalSetting.maxRows


            cursorStyleComboBox.renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    val text = if (value == CursorStyle.Block) "▋" else if (value == CursorStyle.Underline) "▁" else "▏"
                    return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
                }
            }

            cursorStyleComboBox.addItem(CursorStyle.Block)
            cursorStyleComboBox.addItem(CursorStyle.Bar)
            cursorStyleComboBox.addItem(CursorStyle.Underline)

            shellComboBox.renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    val text = when (value) {
                        LocalShellProvider.EmptyCustomCommandLineArgs -> I18n.getString("termora.settings.terminal.custom-command-line")
                        is LocalShellProvider.CustomCommandLineArgs -> value.commandLine.commandLineString
                        is LocalShellProvider.Specify -> value.localShell.toString()
                        else -> value.toString()
                    }
                    return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
                }
            }

            for (localShell in localShells) {
                shellComboBox.addItem(localShell.provider)
            }
            shellComboBox.addItem(LocalShellProvider.EmptyCustomCommandLineArgs)

            val provider = LocalShellProvider.makeProviderByIdentificationString(terminalSetting.localShell)
            if (provider is LocalShellProvider.CustomCommandLineArgs) {
                shellComboBox.isEditable = true
                shellComboBox.selectedItem = provider.commandLine.commandLineString
            } else {
                shellComboBox.selectedItem = provider
            }

            fontComboBox.addItem("JetBrains Mono")
            fontComboBox.addItem("Source Code Pro")

            fontComboBox.selectedItem = terminalSetting.font
            debugComboBox.selectedItem = terminalSetting.debug
            cursorStyleComboBox.selectedItem = terminalSetting.cursor
            selectCopyComboBox.selectedItem = terminalSetting.selectCopy
        }

        override fun getIcon(isSelected: Boolean): Icon {
            return Icons.terminal
        }

        override fun getTitle(): String {
            return I18n.getString("termora.settings.terminal")
        }

        override fun getJComponent(): JComponent {
            return this
        }

        private fun getCenterComponent(): JComponent {
            val layout = FormLayout(
                "left:pref, $formMargin, default:grow, $formMargin, left:pref, $formMargin, pref, default:grow",
                "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
            )

            var rows = 1
            val step = 2
            val panel = FormBuilder.create().layout(layout)
                .debug(false)
                .add("${I18n.getString("termora.settings.terminal.font")}:").xy(1, rows)
                .add(fontComboBox).xy(3, rows)
                .add("${I18n.getString("termora.settings.terminal.size")}:").xy(5, rows)
                .add(fontSizeTextField).xy(7, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.terminal.max-rows")}:").xy(1, rows)
                .add(maxRowsTextField).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.terminal.debug")}:").xy(1, rows)
                .add(debugComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.terminal.select-copy")}:").xy(1, rows)
                .add(selectCopyComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.terminal.cursor-style")}:").xy(1, rows)
                .add(cursorStyleComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.terminal.local-shell")}:").xy(1, rows)
                .add(shellComboBox).xyw(3, rows, 5)
                .build()


            return panel
        }
    }

    private inner class CloudSyncOption : JPanel(BorderLayout()), Option {

        val typeComboBox = FlatComboBox<SyncType>()
        val tokenTextField = OutlinePasswordField(255)
        val gistTextField = OutlineTextField(255)
        val domainTextField = OutlineTextField(255)
        val uploadConfigButton = JButton(I18n.getString("termora.settings.sync.push"), Icons.upload)
        val exportConfigButton = JButton(I18n.getString("termora.settings.sync.export"), Icons.export)
        val downloadConfigButton = JButton(I18n.getString("termora.settings.sync.pull"), Icons.download)
        val lastSyncTimeLabel = JLabel()
        val sync get() = database.sync
        val hostsCheckBox = JCheckBox(I18n.getString("termora.welcome.my-hosts"))
        val keysCheckBox = JCheckBox(I18n.getString("termora.settings.sync.range.keys"))
        val keywordHighlightsCheckBox = JCheckBox(I18n.getString("termora.settings.sync.range.keyword-highlights"))
        val macrosCheckBox = JCheckBox(I18n.getString("termora.macro"))
        val visitGistBtn = JButton(Icons.externalLink)
        val getTokenBtn = JButton(Icons.externalLink)

        init {
            initView()
            initEvents()
            add(getCenterComponent(), BorderLayout.CENTER)
        }

        @OptIn(DelicateCoroutinesApi::class)
        private fun initEvents() {
            downloadConfigButton.addActionListener {
                GlobalScope.launch(Dispatchers.IO) {
                    pushOrPull(false)
                }
            }

            uploadConfigButton.addActionListener {
                GlobalScope.launch(Dispatchers.IO) {
                    pushOrPull(true)
                }
            }

            typeComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    sync.type = typeComboBox.selectedItem as SyncType

                    if (typeComboBox.selectedItem == SyncType.GitLab) {
                        if (domainTextField.text.isBlank()) {
                            domainTextField.text = StringUtils.defaultIfBlank(sync.domain, "https://gitlab.com/api")
                        }
                    }

                    if (typeComboBox.selectedItem == SyncType.Gitee) {
                        gistTextField.trailingComponent = null
                    } else {
                        gistTextField.trailingComponent = visitGistBtn
                    }

                    removeAll()
                    add(getCenterComponent(), BorderLayout.CENTER)
                    revalidate()
                    repaint()
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
                    SyncType.GitLab -> Application.browse(URI.create("https://gitlab.com/-/user_settings/personal_access_tokens"))
                    SyncType.GitHub -> Application.browse(URI.create("https://github.com/settings/tokens"))
                    SyncType.Gitee -> Application.browse(URI.create("https://gitee.com/profile/personal_access_tokens"))
                }
            }

            exportConfigButton.addActionListener { export() }

            keysCheckBox.addActionListener { refreshButtons() }
            hostsCheckBox.addActionListener { refreshButtons() }
            keywordHighlightsCheckBox.addActionListener { refreshButtons() }

        }

        private fun refreshButtons() {
            sync.rangeKeyPairs = keysCheckBox.isSelected
            sync.rangeHosts = hostsCheckBox.isSelected
            sync.rangeKeywordHighlights = keywordHighlightsCheckBox.isSelected

            downloadConfigButton.isEnabled = keysCheckBox.isSelected || hostsCheckBox.isSelected
                    || keywordHighlightsCheckBox.isSelected
            uploadConfigButton.isEnabled = downloadConfigButton.isEnabled
            exportConfigButton.isEnabled = downloadConfigButton.isEnabled
        }

        private fun export() {

            val fileChooser = FileChooser()
            fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
            fileChooser.win32Filters.add(Pair("All Files", listOf("*")))
            fileChooser.win32Filters.add(Pair("JSON files", listOf("json")))
            fileChooser.showSaveDialog(owner, "${Application.getName()}.json").thenAccept { file ->
                if (file != null) {
                    SwingUtilities.invokeLater { exportText(file) }
                }
            }
        }

        private fun exportText(file: File) {
            val syncConfig = getSyncConfig()
            val text = ohMyJson.encodeToString(buildJsonObject {
                val now = System.currentTimeMillis()
                put("exporter", SystemUtils.USER_NAME)
                put("version", Application.getVersion())
                put("exportDate", now)
                put("os", SystemUtils.OS_NAME)
                put("exportDateHuman", DateFormatUtils.ISO_8601_EXTENDED_DATETIME_TIME_ZONE_FORMAT.format(Date(now)))
                if (syncConfig.ranges.contains(SyncRange.Hosts)) {
                    put("hosts", ohMyJson.encodeToJsonElement(HostManager.instance.hosts()))
                }
                if (syncConfig.ranges.contains(SyncRange.KeyPairs)) {
                    put("keyPairs", ohMyJson.encodeToJsonElement(KeyManager.instance.getOhKeyPairs()))
                }
                if (syncConfig.ranges.contains(SyncRange.KeywordHighlights)) {
                    put(
                        "keywordHighlights",
                        ohMyJson.encodeToJsonElement(KeywordHighlightManager.instance.getKeywordHighlights())
                    )
                }
                if (syncConfig.ranges.contains(SyncRange.Macros)) {
                    put(
                        "macros",
                        ohMyJson.encodeToJsonElement(MacroManager.instance.getMacros())
                    )
                }
                put("settings", buildJsonObject {
                    put("appearance", ohMyJson.encodeToJsonElement(database.appearance.getProperties()))
                    put("sync", ohMyJson.encodeToJsonElement(database.sync.getProperties()))
                    put("terminal", ohMyJson.encodeToJsonElement(database.terminal.getProperties()))
                })
            })
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
            return SyncConfig(
                type = typeComboBox.selectedItem as SyncType,
                token = String(tokenTextField.password),
                gistId = gistTextField.text,
                options = mapOf("domain" to domainTextField.text),
                ranges = range
            )
        }

        private suspend fun pushOrPull(push: Boolean) {

            if (typeComboBox.selectedItem == SyncType.GitLab) {
                if (domainTextField.text.isBlank()) {
                    withContext(Dispatchers.Swing) {
                        domainTextField.outline = "error"
                        domainTextField.requestFocusInWindow()
                    }
                    return
                }
            }

            if (tokenTextField.password.isEmpty()) {
                withContext(Dispatchers.Swing) {
                    tokenTextField.outline = "error"
                    tokenTextField.requestFocusInWindow()
                }
                return
            }

            if (gistTextField.text.isBlank() && !push) {
                withContext(Dispatchers.Swing) {
                    gistTextField.outline = "error"
                    gistTextField.requestFocusInWindow()
                }
                return
            }


            // 没有拉取过 && 是推送 && gistId 不为空
            if (!pulled && push && gistTextField.text.isNotBlank()) {
                val code = withContext(Dispatchers.Swing) {
                    // 提示第一次推送
                    OptionPane.showConfirmDialog(
                        owner,
                        I18n.getString("termora.settings.sync.push-warning"),
                        messageType = JOptionPane.WARNING_MESSAGE,
                        optionType = JOptionPane.YES_NO_CANCEL_OPTION,
                        options = arrayOf(
                            uploadConfigButton.text,
                            downloadConfigButton.text,
                            I18n.getString("termora.cancel")
                        ),
                        initialValue = I18n.getString("termora.cancel")
                    )
                }
                when (code) {
                    -1, JOptionPane.CANCEL_OPTION -> return
                    JOptionPane.NO_OPTION -> pushOrPull(false) // pull
                    JOptionPane.YES_OPTION -> pulled = true // force push
                }
            }

            withContext(Dispatchers.Swing) {
                exportConfigButton.isEnabled = false
                downloadConfigButton.isEnabled = false
                uploadConfigButton.isEnabled = false
                typeComboBox.isEnabled = false
                gistTextField.isEnabled = false
                tokenTextField.isEnabled = false
                keysCheckBox.isEnabled = false
                keywordHighlightsCheckBox.isEnabled = false
                hostsCheckBox.isEnabled = false
                domainTextField.isEnabled = false

                if (push) {
                    uploadConfigButton.text = "${I18n.getString("termora.settings.sync.push")}..."
                } else {
                    downloadConfigButton.text = "${I18n.getString("termora.settings.sync.pull")}..."
                }
            }

            val syncConfig = getSyncConfig()

            // sync
            val syncResult = kotlin.runCatching {
                val syncer = SyncerProvider.instance.getSyncer(syncConfig.type)
                if (push) {
                    syncer.push(syncConfig)
                } else {
                    syncer.pull(syncConfig)
                }
            }

            // 恢复状态
            withContext(Dispatchers.Swing) {
                downloadConfigButton.isEnabled = true
                exportConfigButton.isEnabled = true
                uploadConfigButton.isEnabled = true
                keysCheckBox.isEnabled = true
                hostsCheckBox.isEnabled = true
                typeComboBox.isEnabled = true
                gistTextField.isEnabled = true
                tokenTextField.isEnabled = true
                domainTextField.isEnabled = true
                keywordHighlightsCheckBox.isEnabled = true
                if (push) {
                    uploadConfigButton.text = I18n.getString("termora.settings.sync.push")
                } else {
                    downloadConfigButton.text = I18n.getString("termora.settings.sync.pull")
                }
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
                // pulled
                if (!pulled) pulled = !push

                withContext(Dispatchers.Swing) {
                    val now = System.currentTimeMillis()
                    sync.lastSyncTime = now
                    val date = DateFormatUtils.format(Date(now), I18n.getString("termora.date-format"))
                    lastSyncTimeLabel.text = "${I18n.getString("termora.settings.sync.last-sync-time")}: $date"
                    if (push && gistTextField.text.isBlank()) {
                        gistTextField.text = syncResult.map { it.config }.getOrDefault(syncConfig).gistId
                    }
                    OptionPane.showMessageDialog(
                        owner,
                        message = I18n.getString("termora.settings.sync.done"),
                        duration = 1500.milliseconds,
                    )
                }
            }


        }

        private fun initView() {
            typeComboBox.addItem(SyncType.GitHub)
            typeComboBox.addItem(SyncType.GitLab)
            typeComboBox.addItem(SyncType.Gitee)

            hostsCheckBox.isFocusable = false
            keysCheckBox.isFocusable = false
            keywordHighlightsCheckBox.isFocusable = false
            macrosCheckBox.isFocusable = false

            hostsCheckBox.isSelected = sync.rangeHosts
            keysCheckBox.isSelected = sync.rangeKeyPairs
            keywordHighlightsCheckBox.isSelected = sync.rangeKeywordHighlights
            macrosCheckBox.isSelected = sync.rangeMacros

            typeComboBox.selectedItem = sync.type
            gistTextField.text = sync.gist
            tokenTextField.text = sync.token
            domainTextField.trailingComponent = JButton(Icons.externalLink).apply {
                addActionListener {
                    Application.browse(URI.create("https://docs.gitlab.com/ee/api/snippets.html"))
                }
            }

            if (typeComboBox.selectedItem != SyncType.Gitee) {
                gistTextField.trailingComponent = if (gistTextField.text.isNotBlank()) visitGistBtn else null
            }

            tokenTextField.trailingComponent = if (tokenTextField.password.isEmpty()) getTokenBtn else null

            if (typeComboBox.selectedItem == SyncType.GitLab) {
                if (domainTextField.text.isBlank()) {
                    domainTextField.text = StringUtils.defaultIfBlank(sync.domain, "https://gitlab.com/api")
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
                "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
            )

            val rangeBox = FormBuilder.create()
                .layout(
                    FormLayout(
                        "left:pref, $formMargin, left:pref, $formMargin, left:pref",
                        "pref, $formMargin, pref"
                    )
                )
                .add(hostsCheckBox).xy(1, 1)
                .add(keysCheckBox).xy(3, 1)
                .add(keywordHighlightsCheckBox).xy(5, 1)
                .add(macrosCheckBox).xy(1, 3)
                .build()

            var rows = 1
            val step = 2
            val builder = FormBuilder.create().layout(layout).debug(false);
            val box = Box.createHorizontalBox()
            box.add(typeComboBox)
            if (typeComboBox.selectedItem == SyncType.GitLab) {
                box.add(Box.createHorizontalStrut(4))
                box.add(domainTextField)
            }
            builder.add("${I18n.getString("termora.settings.sync.type")}:").xy(1, rows)
                .add(box).xy(3, rows).apply { rows += step }

            builder.add("${I18n.getString("termora.settings.sync.token")}:").xy(1, rows)
                .add(tokenTextField).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.sync.gist")}:").xy(1, rows)
                .add(gistTextField).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.sync.range")}:").xy(1, rows)
                .add(rangeBox).xy(3, rows).apply { rows += step }
                // Sync buttons
                .add(
                    FormBuilder.create()
                        .layout(FormLayout("left:pref, $formMargin, left:pref, $formMargin, left:pref", "pref"))
                        .add(uploadConfigButton).xy(1, 1)
                        .add(downloadConfigButton).xy(3, 1)
                        .add(exportConfigButton).xy(5, 1)
                        .build()
                ).xy(3, rows, "center, fill").apply { rows += step }
                .add(lastSyncTimeLabel).xy(3, rows, "center, fill").apply { rows += step }


            return builder.build()

        }
    }

    private inner class AboutOption : JPanel(BorderLayout()), Option {

        init {
            initView()
            initEvents()
        }


        private fun initView() {
            add(BannerPanel(9, true), BorderLayout.NORTH)
            add(p(), BorderLayout.CENTER)
        }

        private fun p(): JPanel {
            val layout = FormLayout(
                "left:pref, $formMargin, default:grow",
                "pref, 20dlu, pref, 4dlu, pref, 4dlu, pref, 4dlu, pref"
            )


            var rows = 1
            val step = 2

            return FormBuilder.create().padding("$formMargin, $formMargin, $formMargin, $formMargin")
                .layout(layout).debug(true)
                .add(I18n.getString("termora.settings.about.termora", Application.getVersion()))
                .xyw(1, rows, 3, "center, fill").apply { rows += step }
                .add("${I18n.getString("termora.settings.about.author")}:").xy(1, rows)
                .add(createHyperlink("https://github.com/hstyi")).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.about.source")}:").xy(1, rows)
                .add(
                    createHyperlink(
                        "https://github.com/TermoraDev/termora/tree/${Application.getVersion()}",
                        "https://github.com/TermoraDev/termora",
                    )
                ).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.about.issue")}:").xy(1, rows)
                .add(createHyperlink("https://github.com/TermoraDev/termora/issues")).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.about.third-party")}:").xy(1, rows)
                .add(
                    createHyperlink(
                        "https://github.com/TermoraDev/termora/blob/${Application.getVersion()}/THIRDPARTY",
                        "Open-source software"
                    )
                ).xy(3, rows).apply { rows += step }
                .build()


        }

        private fun createHyperlink(url: String, text: String = url): Hyperlink {
            return Hyperlink(object : AnAction(text) {
                override fun actionPerformed(evt: ActionEvent) {
                    Application.browse(URI.create(url))
                }
            });
        }

        private fun initEvents() {}

        override fun getIcon(isSelected: Boolean): Icon {
            return Icons.infoOutline
        }

        override fun getTitle(): String {
            return I18n.getString("termora.settings.about")
        }

        override fun getJComponent(): JComponent {
            return this
        }

    }

    private inner class DoormanOption : JPanel(BorderLayout()), Option {
        private val label = FlatLabel()
        private val icon = JLabel()
        private val passwordTextField = OutlinePasswordField(255)
        private val twoPasswordTextField = OutlinePasswordField(255)
        private val tip = FlatLabel()
        private val safeBtn = FlatButton()
        private val doorman get() = Doorman.instance
        private val hostManager get() = HostManager.instance
        private val keyManager get() = KeyManager.instance

        init {
            initView()
            initEvents()
        }


        private fun initView() {

            label.labelType = FlatLabel.LabelType.h2
            label.horizontalAlignment = SwingConstants.CENTER
            safeBtn.isFocusable = false
            passwordTextField.placeholderText = I18n.getString("termora.setting.security.enter-password")
            twoPasswordTextField.placeholderText = I18n.getString("termora.setting.security.enter-password-again")
            tip.foreground = UIManager.getColor("TextField.placeholderForeground")
            icon.horizontalAlignment = SwingConstants.CENTER

            if (doorman.isWorking()) {
                add(getSafeComponent(), BorderLayout.CENTER)
            } else {
                add(getUnsafeComponent(), BorderLayout.CENTER)
            }

        }

        private fun getCenterComponent(unsafe: Boolean = false): JComponent {
            var rows = 2
            val step = 2

            val panel = if (unsafe) {
                FormBuilder.create().layout(
                    FormLayout(
                        "default:grow, 4dlu, default:grow",
                        "pref"
                    )
                )
                    .add(passwordTextField).xy(1, 1)
                    .add(twoPasswordTextField).xy(3, 1)
                    .build()
            } else passwordTextField

            return FormBuilder.create().debug(false)
                .layout(
                    FormLayout(
                        "$formMargin, default:grow, 4dlu, pref, $formMargin",
                        "15dlu, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin"
                    )
                )
                .add(icon).xyw(2, rows, 4).apply { rows += step }
                .add(label).xyw(2, rows, 4).apply { rows += step }
                .add(panel).xy(2, rows)
                .add(safeBtn).xy(4, rows).apply { rows += step }
                .add(tip).xyw(2, rows, 4, "center, fill").apply { rows += step }
                .build()
        }

        private fun getSafeComponent(): JComponent {
            label.text = I18n.getString("termora.doorman.safe")
            tip.text = I18n.getString("termora.doorman.verify-password")
            icon.icon = FlatSVGIcon(Icons.role.name, 80, 80)
            safeBtn.icon = Icons.unlocked

            safeBtn.actionListeners.forEach { safeBtn.removeActionListener(it) }
            passwordTextField.actionListeners.forEach { passwordTextField.removeActionListener(it) }

            safeBtn.addActionListener { testPassword() }
            passwordTextField.addActionListener { testPassword() }

            return getCenterComponent(false)
        }

        private fun testPassword() {
            if (passwordTextField.password.isEmpty()) {
                passwordTextField.outline = "error"
                passwordTextField.requestFocusInWindow()
            } else {
                if (doorman.test(passwordTextField.password)) {
                    OptionPane.showMessageDialog(
                        owner,
                        I18n.getString("termora.doorman.password-correct"),
                        messageType = JOptionPane.INFORMATION_MESSAGE
                    )
                } else {
                    OptionPane.showMessageDialog(
                        owner,
                        I18n.getString("termora.doorman.password-wrong"),
                        messageType = JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }

        private fun setPassword() {

            if (doorman.isWorking()) {
                return
            }

            if (passwordTextField.password.isEmpty()) {
                passwordTextField.outline = "error"
                passwordTextField.requestFocusInWindow()
                return
            } else if (twoPasswordTextField.password.isEmpty()) {
                twoPasswordTextField.outline = "error"
                twoPasswordTextField.requestFocusInWindow()
                return
            } else if (!twoPasswordTextField.password.contentEquals(passwordTextField.password)) {
                twoPasswordTextField.outline = "error"
                OptionPane.showMessageDialog(
                    owner,
                    I18n.getString("termora.setting.security.password-is-different"),
                    messageType = JOptionPane.ERROR_MESSAGE
                )
                twoPasswordTextField.requestFocusInWindow()
                return
            }

            if (OptionPane.showConfirmDialog(
                    owner, tip.text,
                    optionType = JOptionPane.OK_CANCEL_OPTION
                ) != JOptionPane.OK_OPTION
            ) {
                return
            }

            val hosts = hostManager.hosts()
            val keyPairs = keyManager.getOhKeyPairs()
            // 获取到安全的属性，如果设置密码那表示之前并未加密
            // 这里取出来之后重新存储加密
            val properties = database.getSafetyProperties().map { Pair(it, it.getProperties()) }

            val key = doorman.work(passwordTextField.password)

            hosts.forEach { hostManager.addHost(it, false) }
            keyPairs.forEach { keyManager.addOhKeyPair(it) }
            for (e in properties) {
                for ((k, v) in e.second) {
                    e.first.putString(k, v)
                }
            }

            // 使用助记词对密钥加密
            val mnemonicCode = Mnemonics.MnemonicCode(Mnemonics.WordCount.COUNT_12)
            database.properties.putString(
                "doorman-key-backup",
                AES.ECB.encrypt(mnemonicCode.toEntropy(), key).encodeBase64String()
            )

            val sb = StringBuilder()
            val iterator = mnemonicCode.iterator()
            val group = 4
            val lines = Mnemonics.WordCount.COUNT_12.count / group
            sb.append("<table width=100%>")
            for (i in 0 until lines) {
                sb.append("<tr align=center>")
                for (j in 0 until group) {
                    sb.append("<td>")
                    sb.append(iterator.next())
                    sb.append("</td>")
                }
                sb.append("</tr>")
            }
            sb.append("</table>")

            val pane = JXEditorPane()
            pane.isEditable = false
            pane.contentType = "text/html"
            pane.text =
                """<html><b>${I18n.getString("termora.setting.security.mnemonic-note")}</b><br/><br/>${sb}</html>""".trimIndent()

            OptionPane.showConfirmDialog(
                owner, pane, messageType = JOptionPane.PLAIN_MESSAGE,
                options = arrayOf(I18n.getString("termora.copy")),
                optionType = JOptionPane.YES_OPTION,
                initialValue = I18n.getString("termora.copy")
            )
            // force copy
            toolkit.systemClipboard.setContents(StringSelection(mnemonicCode.joinToString(StringUtils.SPACE)), null)
            mnemonicCode.clear()

            passwordTextField.text = StringUtils.EMPTY

            removeAll()
            add(getSafeComponent(), BorderLayout.CENTER)
            revalidate()
            repaint()
        }

        private fun getUnsafeComponent(): JComponent {
            label.text = I18n.getString("termora.doorman.unsafe")
            tip.text = I18n.getString("termora.doorman.lock-data")
            icon.icon = FlatSVGIcon(Icons.warningDialog.name, 80, 80)
            safeBtn.icon = Icons.locked

            passwordTextField.actionListeners.forEach { passwordTextField.removeActionListener(it) }
            twoPasswordTextField.actionListeners.forEach { twoPasswordTextField.removeActionListener(it) }

            safeBtn.actionListeners.forEach { safeBtn.removeActionListener(it) }
            safeBtn.addActionListener { setPassword() }
            twoPasswordTextField.addActionListener { setPassword() }
            passwordTextField.addActionListener { twoPasswordTextField.requestFocusInWindow() }

            return getCenterComponent(true)
        }


        private fun initEvents() {}

        override fun getIcon(isSelected: Boolean): Icon {
            return Icons.clusterRole
        }

        override fun getTitle(): String {
            return I18n.getString("termora.setting.security")
        }

        override fun getJComponent(): JComponent {
            return this
        }

    }


}