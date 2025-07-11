package app.termora

import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import app.termora.database.DatabaseManager
import app.termora.keymap.KeymapPanel
import app.termora.plugin.ExtensionManager
import app.termora.terminal.CursorStyle
import app.termora.terminal.DataKey
import app.termora.terminal.panel.FloatingToolbarPanel
import app.termora.terminal.panel.TerminalPanel
import app.termora.transfer.TransportTerminalTab
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatComboBox
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatToolBar
import com.formdev.flatlaf.util.SystemInfo
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import com.jthemedetecor.OsThemeDetector
import com.sun.jna.LastErrorException
import com.sun.jna.Native
import com.sun.jna.platform.win32.Shell32
import com.sun.jna.platform.win32.ShlObj
import com.sun.jna.platform.win32.WinDef
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import java.net.URI
import java.util.*
import javax.swing.*
import javax.swing.JSpinner.NumberEditor
import javax.swing.event.DocumentEvent
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener


class SettingsOptionsPane : OptionsPane() {
    private val owner get() = SwingUtilities.getWindowAncestor(this@SettingsOptionsPane)
    private val database get() = DatabaseManager.getInstance()
    private val extensionManager get() = ExtensionManager.getInstance()

    companion object {
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

        val extensions = extensionManager.getExtensions(SettingsOptionExtension::class.java)
        val options = mutableListOf<Option>()

        options.add(AppearanceOption())
        options.add(TerminalOption())
        options.add(KeyShortcutsOption())
        options.add(SFTPOption())
        options.add(AboutOption())

        for (extension in extensions) {
            options.add(extension.createSettingsOption())
        }

        for (option in options) {
            addOption(option)
        }

    }

    override fun addOption(option: Option) {
        super.addOption(option)
    }

    private inner class AppearanceOption : JPanel(BorderLayout()), Option {
        val themeManager = ThemeManager.getInstance()
        val themeComboBox = FlatComboBox<String>()
        val layoutComboBox = FlatComboBox<TermoraLayout>()
        val languageComboBox = FlatComboBox<String>()
        val backgroundComBoBox = YesOrNoComboBox()
        val confirmTabCloseComBoBox = YesOrNoComboBox()
        val followSystemCheckBox = JCheckBox(I18n.getString("termora.settings.appearance.follow-system"))
        val preferredThemeBtn = JButton(Icons.settings)
        val opacitySpinner = NumberSpinner(100, 0, 100)

        private val appearance get() = database.appearance


        init {
            initView()
            initEvents()
        }

        private fun initView() {

            layoutComboBox.addItem(TermoraLayout.Screen)
            layoutComboBox.addItem(TermoraLayout.Fence)
            layoutComboBox.renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    var text = value
                    if (value == TermoraLayout.Screen) {
                        text = I18n.getString("termora.settings.appearance.layout.screen")
                    } else if (value == TermoraLayout.Fence) {
                        text = I18n.getString("termora.settings.appearance.layout.fence")
                    }

                    val c = super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
                    icon = null

                    if (value == TermoraLayout.Screen) {
                        icon = if (isSelected) Icons.uiForm.dark else Icons.uiForm
                    } else if (value == TermoraLayout.Fence) {
                        icon = if (isSelected) Icons.dataColumn.dark else Icons.dataColumn
                    }

                    return c
                }
            }
            layoutComboBox.selectedItem = runCatching { TermoraLayout.valueOf(appearance.layout) }
                .getOrNull() ?: TermoraLayout.Layout

            backgroundComBoBox.isEnabled = SystemInfo.isWindows || SystemInfo.isMacOS

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

            layoutComboBox.addItemListener(object : ItemListener {
                override fun itemStateChanged(e: ItemEvent) {
                    if (e.stateChange == ItemEvent.SELECTED) {
                        appearance.layout = layoutComboBox.selectedItem?.toString() ?: return
                        if (TermoraLayout.Layout.name != appearance.layout) {
                            SwingUtilities.invokeLater { TermoraRestarter.getInstance().scheduleRestart(owner) }
                        }
                    }
                }
            })

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

        }

        override fun getIcon(isSelected: Boolean): Icon {
            return Icons.uiForm
        }

        override fun getTitle(): String {
            return I18n.getString("termora.settings.appearance")
        }

        override fun getIdentifier(): String {
            return "Appearance"
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
                "left:pref, $FORM_MARGIN, default:grow, $FORM_MARGIN, default, default:grow",
                "pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref"
            )
            val box = FlatToolBar()
            box.add(followSystemCheckBox)
            box.add(Box.createHorizontalStrut(2))
            box.add(preferredThemeBtn)

            var rows = 1
            val step = 2
            val builder = FormBuilder.create().layout(layout).debug(false)
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


            builder.add("${I18n.getString("termora.settings.appearance.layout")}:").xy(1, rows)
                .add(layoutComboBox).xy(3, rows).apply { rows += step }

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
        private val fontComboBox = FontComboBox()
        private val fallbackFontComboBox = FontComboBox()
        private val shellComboBox = FlatComboBox<String>()
        private val maxRowsTextField = IntSpinner(0, 0)
        private val fontSizeTextField = IntSpinner(0, 9, 99)
        private val terminalSetting get() = DatabaseManager.getInstance().terminal
        private val selectCopyComboBox = YesOrNoComboBox()
        private val autoCloseTabComboBox = YesOrNoComboBox()
        private val floatingToolbarComboBox = YesOrNoComboBox()
        private val hyperlinkComboBox = YesOrNoComboBox()
        private var isInitialized = false

        private fun initEvents() {
            fontComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    terminalSetting.font = fontComboBox.selectedItem as String
                    fireFontChanged()
                }
            }

            fallbackFontComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    terminalSetting.fallbackFont = fallbackFontComboBox.selectedItem as String
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

            cursorStyleComboBox.addItem(CursorStyle.Block)
            cursorStyleComboBox.addItem(CursorStyle.Bar)
            cursorStyleComboBox.addItem(CursorStyle.Underline)

            shellComboBox.isEditable = true

            for (localShell in localShells) {
                shellComboBox.addItem(localShell)
            }

            shellComboBox.selectedItem = terminalSetting.localShell

            fontComboBox.addItem(terminalSetting.font)
            val items = fontComboBox.getItems()
            for (family in listOf("JetBrains Mono", "Source Code Pro", "Monospaced")) {
                if (items.contains(family).not()) fontComboBox.addItem(family)
            }

            if (terminalSetting.fallbackFont.isNotBlank()) {
                fallbackFontComboBox.addItem(StringUtils.EMPTY)
            }
            fallbackFontComboBox.addItem(terminalSetting.fallbackFont)

            fontComboBox.selectedItem = terminalSetting.font
            fallbackFontComboBox.selectedItem = terminalSetting.fallbackFont
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

        /**
         * 因为字体初始化很慢，这里只有选中的时候才初始化
         */
        override fun onSelected() {
            if (isInitialized) return

            initView()
            initEvents()
            add(getCenterComponent(), BorderLayout.CENTER)

            isInitialized = true
        }

        private fun getCenterComponent(): JComponent {
            val layout = FormLayout(
                "left:pref, $FORM_MARGIN, default:grow, $FORM_MARGIN, left:pref, $FORM_MARGIN, pref, default:grow",
                "pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref"
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
                .add("${I18n.getString("termora.settings.terminal.fallback-font")}:").xy(1, rows)
                .add(fallbackFontComboBox).xy(3, rows).apply { rows += step }
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
                            if (manager.getTerminalTabs().none { it is TransportTerminalTab }) {
                                manager.addTerminalTab(1, TransportTerminalTab(), false)
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
            return I18n.getString("termora.transport.sftp")
        }

        override fun getJComponent(): JComponent {
            return this
        }

        private fun getCenterComponent(): JComponent {
            val layout = FormLayout(
                "left:pref, $FORM_MARGIN, default:grow, 30dlu",
                "pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref"
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
                "left:pref, $FORM_MARGIN, default:grow",
                "pref, 20dlu, pref, 4dlu, pref, 4dlu, pref, 4dlu, pref, 4dlu, pref"
            )


            var rows = 1
            val step = 2

            val branch = if (Application.isUnknownVersion()) "main" else Application.getVersion()

            val builder = FormBuilder.create().padding("$FORM_MARGIN, $FORM_MARGIN, $FORM_MARGIN, $FORM_MARGIN")
                .layout(layout).debug(false)
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

            if (I18n.isChinaMainland()) {
                builder.add("交流群:").xy(1, rows)
                    .add(createHyperlink("https://www.termora.cn/muted/discussion-group", "Discussion Group"))
                    .xy(3, rows).apply { rows += step }
            }

            return builder.build()

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

        override fun getAnchor(): Anchor {
            return Anchor.Last
        }

        override fun getIdentifier(): String {
            return "About"
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