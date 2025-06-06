package app.termora

import app.termora.AES.encodeBase64String
import app.termora.Application.ohMyJson
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import app.termora.highlight.KeywordHighlight
import app.termora.highlight.KeywordHighlightManager
import app.termora.keymap.Keymap
import app.termora.keymap.KeymapManager
import app.termora.keymap.KeymapPanel
import app.termora.keymgr.KeyManager
import app.termora.keymgr.OhKeyPair
import app.termora.macro.Macro
import app.termora.macro.MacroManager
import app.termora.native.FileChooser
import app.termora.sftp.SFTPTab
import app.termora.snippet.Snippet
import app.termora.snippet.SnippetManager
import app.termora.sync.*
import app.termora.terminal.CursorStyle
import app.termora.terminal.DataKey
import app.termora.terminal.panel.FloatingToolbarPanel
import app.termora.terminal.panel.TerminalPanel
import cash.z.ecc.android.bip39.Mnemonics
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.*
import com.formdev.flatlaf.util.FontUtils
import com.formdev.flatlaf.util.SystemInfo
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import com.jthemedetecor.OsThemeDetector
import com.sun.jna.LastErrorException
import com.sun.jna.Native
import com.sun.jna.platform.win32.Shell32
import com.sun.jna.platform.win32.ShlObj
import com.sun.jna.platform.win32.WinDef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.commons.lang3.time.DateFormatUtils
import org.jdesktop.swingx.JXEditorPane
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.function.Consumer
import javax.swing.*
import javax.swing.JSpinner.NumberEditor
import javax.swing.event.DocumentEvent
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener


class SettingsOptionsPane : OptionsPane() {
    private val owner get() = SwingUtilities.getWindowAncestor(this@SettingsOptionsPane)
    private val database get() = Database.getDatabase()
    private val hostManager get() = HostManager.getInstance()
    private val snippetManager get() = SnippetManager.getInstance()
    private val keymapManager get() = KeymapManager.getInstance()
    private val macroManager get() = MacroManager.getInstance()
    private val keywordHighlightManager get() = KeywordHighlightManager.getInstance()
    private val keyManager get() = KeyManager.getInstance()

    companion object {
        private val log = LoggerFactory.getLogger(SettingsOptionsPane::class.java)
        private val localShells by lazy { loadShells() }

        private fun loadShells(): List<String> {
            val shells = mutableListOf<String>()
            if (SystemInfo.isWindows) {
                shells.add("cmd.exe")
                shells.add("powershell.exe")
            } else {
                kotlin.runCatching {
                    val process = ProcessBuilder("cat", "/etc/shells").start()
                    if (process.waitFor() != 0) {
                        throw LastErrorException(process.exitValue())
                    }
                    process.inputStream.use { input ->
                        String(input.readAllBytes()).lines()
                            .filter { e -> !e.trim().startsWith('#') }
                            .filter { e -> e.isNotBlank() }
                            .forEach { shells.add(it.trim()) }
                    }
                }.onFailure {
                    shells.add("/bin/bash")
                    shells.add("/bin/csh")
                    shells.add("/bin/dash")
                    shells.add("/bin/ksh")
                    shells.add("/bin/sh")
                    shells.add("/bin/tcsh")
                    shells.add("/bin/zsh")
                }
            }
            return shells
        }


    }

    init {
        addOption(AppearanceOption())
        addOption(TerminalOption())
        addOption(KeyShortcutsOption())
        addOption(SFTPOption())
        addOption(CloudSyncOption())
        addOption(DoormanOption())
        addOption(AboutOption())
        setContentBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8))
    }

    private inner class AppearanceOption : JPanel(BorderLayout()), Option {
        val themeManager = ThemeManager.getInstance()
        val themeComboBox = FlatComboBox<String>()
        val languageComboBox = FlatComboBox<String>()
        val backgroundComBoBox = YesOrNoComboBox()
        val confirmTabCloseComBoBox = YesOrNoComboBox()
        val followSystemCheckBox = JCheckBox(I18n.getString("termora.settings.appearance.follow-system"))
        val preferredThemeBtn = JButton(Icons.settings)
        val opacitySpinner = NumberSpinner(100, 0, 100)
        val backgroundImageTextField = OutlineTextField()

        private val appearance get() = database.appearance
        private val backgroundButton = JButton(Icons.folder)
        private val backgroundClearButton = FlatButton()

        init {
            initView()
            initEvents()
        }

        private fun initView() {

            backgroundComBoBox.isEnabled = SystemInfo.isWindows || SystemInfo.isMacOS
            backgroundImageTextField.isEditable = false
            backgroundImageTextField.trailingComponent = backgroundButton
            backgroundImageTextField.text = FilenameUtils.getName(appearance.backgroundImage)
            backgroundImageTextField.document.addDocumentListener(object : DocumentAdaptor() {
                override fun changedUpdate(e: DocumentEvent) {
                    backgroundClearButton.isEnabled = backgroundImageTextField.text.isNotBlank()
                }
            })

            backgroundClearButton.isFocusable = false
            backgroundClearButton.isEnabled = backgroundImageTextField.text.isNotBlank()
            backgroundClearButton.icon = Icons.delete
            backgroundClearButton.buttonType = FlatButton.ButtonType.toolBarButton


            opacitySpinner.isEnabled = SystemInfo.isMacOS || SystemInfo.isWindows
            opacitySpinner.model = object : SpinnerNumberModel(appearance.opacity, 0.1, 1.0, 0.1) {
                override fun getNextValue(): Any {
                    return super.getNextValue() ?: maximum
                }

                override fun getPreviousValue(): Any {
                    return super.getPreviousValue() ?: minimum
                }
            }
            opacitySpinner.editor = NumberEditor(opacitySpinner, "#.##")
            opacitySpinner.model.stepSize = 0.05

            followSystemCheckBox.isSelected = appearance.followSystem
            preferredThemeBtn.isEnabled = followSystemCheckBox.isSelected
            backgroundComBoBox.selectedItem = appearance.backgroundRunning
            confirmTabCloseComBoBox.selectedItem = appearance.confirmTabClose

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

            opacitySpinner.addChangeListener {
                val opacity = opacitySpinner.value
                if (opacity is Double) {
                    TermoraFrameManager.getInstance().setOpacity(opacity)
                    appearance.opacity = opacity
                }
            }

            backgroundComBoBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    appearance.backgroundRunning = backgroundComBoBox.selectedItem as Boolean
                }
            }


            confirmTabCloseComBoBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    appearance.confirmTabClose = confirmTabCloseComBoBox.selectedItem as Boolean
                }
            }

            followSystemCheckBox.addActionListener {
                appearance.followSystem = followSystemCheckBox.isSelected
                themeComboBox.isEnabled = !followSystemCheckBox.isSelected
                preferredThemeBtn.isEnabled = followSystemCheckBox.isSelected
                appearance.theme = themeComboBox.selectedItem as String

                if (followSystemCheckBox.isSelected) {
                    SwingUtilities.invokeLater {
                        if (OsThemeDetector.getDetector().isDark) {
                            themeManager.change(appearance.darkTheme)
                            themeComboBox.selectedItem = appearance.darkTheme
                        } else {
                            themeManager.change(appearance.lightTheme)
                            themeComboBox.selectedItem = appearance.lightTheme
                        }
                    }
                }
            }

            languageComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    appearance.language = languageComboBox.selectedItem as String
                    SwingUtilities.invokeLater {
                        TermoraRestarter.getInstance().scheduleRestart(owner)
                    }
                }
            }

            preferredThemeBtn.addActionListener { showPreferredThemeContextmenu() }

            backgroundButton.addActionListener {
                val chooser = FileChooser()
                chooser.osxAllowedFileTypes = listOf("png", "jpg", "jpeg")
                chooser.allowsMultiSelection = false
                chooser.win32Filters.add(Pair("Image files", listOf("png", "jpg", "jpeg")))
                chooser.fileSelectionMode = JFileChooser.FILES_ONLY
                chooser.showOpenDialog(owner).thenAccept {
                    if (it.isNotEmpty()) {
                        onSelectedBackgroundImage(it.first())
                    }
                }
            }

            backgroundClearButton.addActionListener {
                BackgroundManager.getInstance().clearBackgroundImage()
                backgroundImageTextField.text = StringUtils.EMPTY
            }
        }

        private fun onSelectedBackgroundImage(file: File) {
            try {
                val destFile = FileUtils.getFile(Application.getBaseDataDir(), "background", file.name)
                FileUtils.forceMkdirParent(destFile)
                FileUtils.deleteQuietly(destFile)
                FileUtils.copyFile(file, destFile, StandardCopyOption.REPLACE_EXISTING)
                backgroundImageTextField.text = destFile.name
                BackgroundManager.getInstance().setBackgroundImage(destFile)
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
                SwingUtilities.invokeLater {
                    OptionPane.showMessageDialog(
                        owner,
                        ExceptionUtils.getRootCauseMessage(e),
                        messageType = JOptionPane.ERROR_MESSAGE
                    )
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

        private fun showPreferredThemeContextmenu() {
            val popupMenu = FlatPopupMenu()
            val dark = JMenu("For Dark OS")
            val light = JMenu("For Light OS")
            val darkTheme = appearance.darkTheme
            val lightTheme = appearance.lightTheme

            for (e in themeManager.themes) {
                val clazz = Class.forName(e.value)
                val item = JCheckBoxMenuItem(e.key)
                item.isSelected = e.key == lightTheme || e.key == darkTheme
                if (clazz.interfaces.contains(DarkLafTag::class.java)) {
                    dark.add(item).addActionListener {
                        if (e.key != darkTheme) {
                            appearance.darkTheme = e.key
                            if (OsThemeDetector.getDetector().isDark) {
                                themeComboBox.selectedItem = e.key
                            }
                        }
                    }
                } else if (clazz.interfaces.contains(LightLafTag::class.java)) {
                    light.add(item).addActionListener {
                        if (e.key != lightTheme) {
                            appearance.lightTheme = e.key
                            if (!OsThemeDetector.getDetector().isDark) {
                                themeComboBox.selectedItem = e.key
                            }
                        }
                    }
                }
            }

            popupMenu.add(dark)
            popupMenu.addSeparator()
            popupMenu.add(light)
            popupMenu.addPopupMenuListener(object : PopupMenuListener {
                override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {

                }

                override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
                }

                override fun popupMenuCanceled(e: PopupMenuEvent) {
                }

            })

            popupMenu.show(preferredThemeBtn, 0, preferredThemeBtn.height + 2)
        }


        private fun getFormPanel(): JPanel {
            val layout = FormLayout(
                "left:pref, $formMargin, default:grow, $formMargin, default, default:grow",
                "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
            )
            val box = FlatToolBar()
            box.add(followSystemCheckBox)
            box.add(Box.createHorizontalStrut(2))
            box.add(preferredThemeBtn)

            var rows = 1
            val step = 2
            val builder = FormBuilder.create().layout(layout)
                .add("${I18n.getString("termora.settings.appearance.theme")}:").xy(1, rows)
                .add(themeComboBox).xy(3, rows)
                .add(box).xy(5, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.appearance.language")}:").xy(1, rows)
                .add(languageComboBox).xy(3, rows)
                .add(Hyperlink(object : AnAction(I18n.getString("termora.settings.appearance.i-want-to-translate")) {
                    override fun actionPerformed(evt: AnActionEvent) {
                        Application.browse(URI.create("https://github.com/TermoraDev/termora/tree/main/src/main/resources/i18n"))
                    }
                })).xy(5, rows).apply { rows += step }


            val bgClearBox = Box.createHorizontalBox()
            bgClearBox.add(backgroundClearButton)
            builder.add("${I18n.getString("termora.settings.appearance.background-image")}:").xy(1, rows)
                .add(backgroundImageTextField).xy(3, rows)
                .add(bgClearBox).xy(5, rows)
                .apply { rows += step }

            builder.add("${I18n.getString("termora.settings.appearance.opacity")}:").xy(1, rows)
                .add(opacitySpinner).xy(3, rows).apply { rows += step }

            builder.add("${I18n.getString("termora.settings.appearance.background-running")}:").xy(1, rows)
                .add(backgroundComBoBox).xy(3, rows).apply { rows += step }

            val confirmTabCloseBox = Box.createHorizontalBox()
            confirmTabCloseBox.add(JLabel("${I18n.getString("termora.settings.appearance.confirm-tab-close")}:"))
            confirmTabCloseBox.add(Box.createHorizontalStrut(8))
            confirmTabCloseBox.add(confirmTabCloseComBoBox)
            builder.add(confirmTabCloseBox).xyw(1, rows, 3).apply { rows += step }

            return builder.build()
        }


    }

    private inner class TerminalOption : JPanel(BorderLayout()), Option {
        private val cursorStyleComboBox = FlatComboBox<CursorStyle>()
        private val debugComboBox = YesOrNoComboBox()
        private val beepComboBox = YesOrNoComboBox()
        private val cursorBlinkComboBox = YesOrNoComboBox()
        private val fontComboBox = FlatComboBox<String>()
        private val shellComboBox = FlatComboBox<String>()
        private val maxRowsTextField = IntSpinner(0, 0)
        private val fontSizeTextField = IntSpinner(0, 9, 99)
        private val terminalSetting get() = Database.getDatabase().terminal
        private val selectCopyComboBox = YesOrNoComboBox()
        private val autoCloseTabComboBox = YesOrNoComboBox()
        private val floatingToolbarComboBox = YesOrNoComboBox()
        private val hyperlinkComboBox = YesOrNoComboBox()

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

            autoCloseTabComboBox.addItemListener { e ->
                if (e.stateChange == ItemEvent.SELECTED) {
                    terminalSetting.autoCloseTabWhenDisconnected = autoCloseTabComboBox.selectedItem as Boolean
                }
            }
            autoCloseTabComboBox.toolTipText = I18n.getString("termora.settings.terminal.auto-close-tab-description")

            floatingToolbarComboBox.addItemListener { e ->
                if (e.stateChange == ItemEvent.SELECTED) {
                    terminalSetting.floatingToolbar = floatingToolbarComboBox.selectedItem as Boolean
                    TerminalPanelFactory.getInstance().getTerminalPanels().forEach { tp ->
                        if (terminalSetting.floatingToolbar && FloatingToolbarPanel.isPined) {
                            tp.getData(FloatingToolbarPanel.FloatingToolbar)?.triggerShow()
                        } else {
                            tp.getData(FloatingToolbarPanel.FloatingToolbar)?.triggerHide()
                        }
                    }
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
                    TerminalFactory.getInstance().getTerminals().forEach { e ->
                        e.getTerminalModel().setData(DataKey.CursorStyle, style)
                    }
                }
            }


            debugComboBox.addItemListener { e ->
                if (e.stateChange == ItemEvent.SELECTED) {
                    terminalSetting.debug = debugComboBox.selectedItem as Boolean
                    TerminalFactory.getInstance().getTerminals().forEach {
                        it.getTerminalModel().setData(TerminalPanel.Debug, terminalSetting.debug)
                    }
                }
            }


            beepComboBox.addItemListener { e ->
                if (e.stateChange == ItemEvent.SELECTED) {
                    terminalSetting.beep = beepComboBox.selectedItem as Boolean
                }
            }

            hyperlinkComboBox.addItemListener { e ->
                if (e.stateChange == ItemEvent.SELECTED) {
                    terminalSetting.hyperlink = hyperlinkComboBox.selectedItem as Boolean
                    TerminalPanelFactory.getInstance().repaintAll()
                }
            }

            cursorBlinkComboBox.addItemListener { e ->
                if (e.stateChange == ItemEvent.SELECTED) {
                    terminalSetting.cursorBlink = cursorBlinkComboBox.selectedItem as Boolean
                }
            }


            shellComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    terminalSetting.localShell = shellComboBox.selectedItem as String
                }
            }

        }

        private fun fireFontChanged() {
            TerminalPanelFactory.getInstance()
                .fireResize()
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

            fontComboBox.renderer = object : DefaultListCellRenderer() {
                init {
                    preferredSize = Dimension(preferredSize.width, fontComboBox.preferredSize.height - 2)
                    maximumSize = Dimension(preferredSize.width, preferredSize.height)
                }

                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    if (value is String) {
                        return super.getListCellRendererComponent(
                            list,
                            "<html><font face='$value'>$value</font></html>",
                            index,
                            isSelected,
                            cellHasFocus
                        )
                    }
                    return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                }
            }
            fontComboBox.maximumSize = fontComboBox.preferredSize

            cursorStyleComboBox.addItem(CursorStyle.Block)
            cursorStyleComboBox.addItem(CursorStyle.Bar)
            cursorStyleComboBox.addItem(CursorStyle.Underline)

            shellComboBox.isEditable = true

            for (localShell in localShells) {
                shellComboBox.addItem(localShell)
            }

            shellComboBox.selectedItem = terminalSetting.localShell

            fontComboBox.addItem(terminalSetting.font)
            var fontsLoaded = false

            fontComboBox.addPopupMenuListener(object : PopupMenuListener {
                override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
                    if (!fontsLoaded) {
                        val selectedItem = fontComboBox.selectedItem
                        fontComboBox.removeAllItems();
                        val fonts = linkedSetOf("JetBrains Mono", "Source Code Pro", "Monospaced")
                        FontUtils.getAvailableFontFamilyNames().forEach {
                            if (!fonts.contains(it)) {
                                fonts.addLast(it)
                                fontComboBox.addItem(it)
                            }
                        }
                        fontComboBox.selectedItem = selectedItem
                        fontsLoaded = true
                    }
                }

                override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {}
                override fun popupMenuCanceled(e: PopupMenuEvent) {}
            })

            fontComboBox.selectedItem = terminalSetting.font
            debugComboBox.selectedItem = terminalSetting.debug
            beepComboBox.selectedItem = terminalSetting.beep
            hyperlinkComboBox.selectedItem = terminalSetting.hyperlink
            cursorBlinkComboBox.selectedItem = terminalSetting.cursorBlink
            cursorStyleComboBox.selectedItem = terminalSetting.cursor
            selectCopyComboBox.selectedItem = terminalSetting.selectCopy
            autoCloseTabComboBox.selectedItem = terminalSetting.autoCloseTabWhenDisconnected
            floatingToolbarComboBox.selectedItem = terminalSetting.floatingToolbar
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
                "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
            )

            val beepBtn = JButton(Icons.run)
            beepBtn.isFocusable = false
            beepBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
            beepBtn.addActionListener { Toolkit.getDefaultToolkit().beep() }

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
                .add("${I18n.getString("termora.settings.terminal.beep")}:").xy(1, rows)
                .add(beepComboBox).xy(3, rows)
                .add(beepBtn).xy(5, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.terminal.hyperlink")}:").xy(1, rows)
                .add(hyperlinkComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.terminal.select-copy")}:").xy(1, rows)
                .add(selectCopyComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.terminal.cursor-style")}:").xy(1, rows)
                .add(cursorStyleComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.terminal.cursor-blink")}:").xy(1, rows)
                .add(cursorBlinkComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.terminal.floating-toolbar")}:").xy(1, rows)
                .add(floatingToolbarComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.terminal.auto-close-tab")}:").xy(1, rows)
                .add(autoCloseTabComboBox).xy(3, rows).apply { rows += step }
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
        val policyComboBox = JComboBox<SyncPolicy>()
        val domainTextField = OutlineTextField(255)
        val syncConfigButton = JButton(I18n.getString("termora.settings.sync"), Icons.settingSync)
        val exportConfigButton = JButton(I18n.getString("termora.settings.sync.export"), Icons.export)
        val importConfigButton = JButton(I18n.getString("termora.settings.sync.import"), Icons.import)
        val lastSyncTimeLabel = JLabel()
        val sync get() = database.sync
        val hostsCheckBox = JCheckBox(I18n.getString("termora.welcome.my-hosts"))
        val keysCheckBox = JCheckBox(I18n.getString("termora.settings.sync.range.keys"))
        val snippetsCheckBox = JCheckBox(I18n.getString("termora.snippet.title"))
        val keywordHighlightsCheckBox = JCheckBox(I18n.getString("termora.settings.sync.range.keyword-highlights"))
        val macrosCheckBox = JCheckBox(I18n.getString("termora.macro"))
        val keymapCheckBox = JCheckBox(I18n.getString("termora.settings.keymap"))
        val visitGistBtn = JButton(Icons.externalLink)
        val getTokenBtn = JButton(Icons.externalLink)

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
                if (hostsCheckBox.isSelected) {
                    for (window in TermoraFrameManager.getInstance().getWindows()) {
                        visit(window.rootPane) {
                            if (it is NewHostTree) it.refreshNode()
                        }
                    }
                }
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
                            keyManager.addOhKeyPair(keyPair)
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
                                keywordHighlightManager.addKeywordHighlight(keyPair)
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
                    put("sync", ohMyJson.encodeToJsonElement(database.sync.getProperties()))
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
            val syncResult = kotlin.runCatching {
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

    private inner class SFTPOption : JPanel(BorderLayout()), Option {

        private val editCommandField = OutlineTextField(255)
        private val sftpCommandField = OutlineTextField(255)
        private val defaultDirectoryField = OutlineTextField(255)
        private val browseDirectoryBtn = JButton(Icons.folder)
        private val browseEditCommandBtn = JButton(Icons.folder)
        private val pinTabComboBox = YesOrNoComboBox()
        private val preserveModificationTimeComboBox = YesOrNoComboBox()
        private val sftp get() = database.sftp

        init {
            initView()
            initEvents()
            add(getCenterComponent(), BorderLayout.CENTER)
        }

        private fun initEvents() {
            editCommandField.document.addDocumentListener(object : DocumentAdaptor() {
                override fun changedUpdate(e: DocumentEvent) {
                    sftp.editCommand = editCommandField.text
                }
            })


            sftpCommandField.document.addDocumentListener(object : DocumentAdaptor() {
                override fun changedUpdate(e: DocumentEvent) {
                    sftp.sftpCommand = sftpCommandField.text
                }
            })

            defaultDirectoryField.document.addDocumentListener(object : DocumentAdaptor() {
                override fun changedUpdate(e: DocumentEvent) {
                    sftp.defaultDirectory = defaultDirectoryField.text
                }
            })

            pinTabComboBox.addItemListener(object : ItemListener {
                override fun itemStateChanged(e: ItemEvent) {
                    if (e.stateChange != ItemEvent.SELECTED) return
                    sftp.pinTab = pinTabComboBox.selectedItem as Boolean
                    for (window in TermoraFrameManager.getInstance().getWindows()) {
                        val evt = AnActionEvent(window, StringUtils.EMPTY, EventObject(window))
                        val manager = evt.getData(DataProviders.TerminalTabbedManager) ?: continue

                        if (sftp.pinTab) {
                            if (manager.getTerminalTabs().none { it is SFTPTab }) {
                                manager.addTerminalTab(1, SFTPTab(), false)
                            }
                        }

                        // 刷新状态
                        manager.refreshTerminalTabs()
                    }
                }

            })

            preserveModificationTimeComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    sftp.preserveModificationTime = preserveModificationTimeComboBox.selectedItem as Boolean
                }
            }

            browseDirectoryBtn.addActionListener(object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    val chooser = FileChooser()
                    chooser.allowsMultiSelection = false
                    chooser.defaultDirectory = StringUtils.defaultIfBlank(
                        defaultDirectoryField.text,
                        SystemUtils.USER_HOME
                    )
                    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    chooser.showOpenDialog(owner).thenAccept { files ->
                        if (files.isNotEmpty()) defaultDirectoryField.text = files.first().absolutePath
                    }
                }
            })

            browseEditCommandBtn.addActionListener(object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    val chooser = FileChooser()
                    chooser.allowsMultiSelection = false
                    chooser.fileSelectionMode = JFileChooser.FILES_ONLY

                    if (SystemInfo.isMacOS) {
                        chooser.defaultDirectory = "/Applications"
                    } else {
                        if (SystemInfo.isWindows) {
                            val pszPath = CharArray(WinDef.MAX_PATH)
                            Shell32.INSTANCE.SHGetFolderPath(
                                null,
                                ShlObj.CSIDL_DESKTOPDIRECTORY, null, ShlObj.SHGFP_TYPE_CURRENT,
                                pszPath
                            )
                            chooser.defaultDirectory = Native.toString(pszPath)
                        } else {
                            chooser.defaultDirectory = SystemUtils.USER_HOME
                        }
                    }

                    chooser.showOpenDialog(owner).thenAccept { files ->
                        if (files.isNotEmpty()) {
                            val file = files.first()
                            if (SystemInfo.isMacOS) {
                                editCommandField.text = "open -a ${file.absolutePath} {0}"
                            } else {
                                editCommandField.text = "${file.absolutePath} {0}"
                            }
                        }
                    }
                }
            })
        }


        private fun initView() {
            if (SystemInfo.isWindows || SystemInfo.isLinux) {
                editCommandField.placeholderText = "notepad {0}"
            } else if (SystemInfo.isMacOS) {
                editCommandField.placeholderText = "open -a TextEdit {0}"
            }

            if (SystemInfo.isWindows) {
                sftpCommandField.placeholderText = "sftp.exe"
            } else {
                sftpCommandField.placeholderText = "sftp"
            }

            editCommandField.trailingComponent = browseEditCommandBtn

            defaultDirectoryField.placeholderText = SystemUtils.USER_HOME
            defaultDirectoryField.trailingComponent = browseDirectoryBtn

            defaultDirectoryField.text = sftp.defaultDirectory
            editCommandField.text = sftp.editCommand
            sftpCommandField.text = sftp.sftpCommand
            pinTabComboBox.selectedItem = sftp.pinTab
            preserveModificationTimeComboBox.selectedItem = sftp.preserveModificationTime
        }

        override fun getIcon(isSelected: Boolean): Icon {
            return Icons.folder
        }

        override fun getTitle(): String {
            return "SFTP"
        }

        override fun getJComponent(): JComponent {
            return this
        }

        private fun getCenterComponent(): JComponent {
            val layout = FormLayout(
                "left:pref, $formMargin, default:grow, 30dlu",
                "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
            )

            val box = Box.createHorizontalBox()
            box.add(JLabel("${I18n.getString("termora.settings.sftp.preserve-time")}:"))
            box.add(Box.createHorizontalStrut(8))
            box.add(preserveModificationTimeComboBox)

            var rows = 1
            val builder = FormBuilder.create().layout(layout).debug(false)
            builder.add("${I18n.getString("termora.settings.sftp.fixed-tab")}:").xy(1, rows)
            builder.add(pinTabComboBox).xy(3, rows).apply { rows += 2 }
            builder.add("${I18n.getString("termora.settings.sftp.edit-command")}:").xy(1, rows)
            builder.add(editCommandField).xy(3, rows).apply { rows += 2 }
            builder.add("${I18n.getString("termora.tabbed.contextmenu.sftp-command")}:").xy(1, rows)
            builder.add(sftpCommandField).xy(3, rows).apply { rows += 2 }
            builder.add("${I18n.getString("termora.settings.sftp.default-directory")}:").xy(1, rows)
            builder.add(defaultDirectoryField).xy(3, rows).apply { rows += 2 }
            builder.add(box).xyw(1, rows, 3).apply { rows += 2 }


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

            val branch = if (Application.isUnknownVersion()) "main" else Application.getVersion()

            return FormBuilder.create().padding("$formMargin, $formMargin, $formMargin, $formMargin")
                .layout(layout).debug(true)
                .add(I18n.getString("termora.settings.about.termora", Application.getVersion()))
                .xyw(1, rows, 3, "center, fill").apply { rows += step }
                .add("${I18n.getString("termora.settings.about.author")}:").xy(1, rows)
                .add(createHyperlink("https://github.com/hstyi")).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.about.source")}:").xy(1, rows)
                .add(
                    createHyperlink(
                        "https://github.com/TermoraDev/termora/tree/${branch}",
                        "https://github.com/TermoraDev/termora",
                    )
                ).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.about.issue")}:").xy(1, rows)
                .add(createHyperlink("https://github.com/TermoraDev/termora/issues")).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.settings.about.third-party")}:").xy(1, rows)
                .add(
                    createHyperlink(
                        "https://github.com/TermoraDev/termora/blob/${branch}/THIRDPARTY",
                        "Open-source software"
                    )
                ).xy(3, rows).apply { rows += step }
                .build()


        }

        private fun createHyperlink(url: String, text: String = url): Hyperlink {
            return Hyperlink(object : AnAction(text) {
                override fun actionPerformed(evt: AnActionEvent) {
                    Application.browse(URI.create(url))
                }
            })
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
        private val doorman get() = Doorman.getInstance()

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
            val snippets = snippetManager.snippets()

            // 获取到安全的属性，如果设置密码那表示之前并未加密
            // 这里取出来之后重新存储加密
            val properties = database.getSafetyProperties().map { Pair(it, it.getProperties()) }
            val key = doorman.work(passwordTextField.password)

            hosts.forEach { hostManager.addHost(it) }
            snippets.forEach { snippetManager.addSnippet(it) }
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

    private inner class KeyShortcutsOption : JPanel(BorderLayout()), Option {

        private val keymapPanel = KeymapPanel()

        init {
            initView()
            initEvents()
        }


        private fun initView() {
            add(keymapPanel, BorderLayout.CENTER)
        }


        private fun initEvents() {}

        override fun getIcon(isSelected: Boolean): Icon {
            return Icons.fitContent
        }

        override fun getTitle(): String {
            return I18n.getString("termora.settings.keymap")
        }

        override fun getJComponent(): JComponent {
            return this
        }

    }

}