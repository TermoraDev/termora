package app.termora.plugins.migration

import app.termora.*
import app.termora.AES.decodeBase64
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.terminal.ControlCharacters
import cash.z.ecc.android.bip39.Mnemonics
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatButton
import com.formdev.flatlaf.extras.components.FlatLabel
import com.formdev.flatlaf.util.SystemInfo
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.JXHyperlink
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.math.max

class DoormanDialog(owner: Window?) : DialogWrapper(owner) {
    companion object {
        private val log = LoggerFactory.getLogger(DoormanDialog::class.java)
    }

    private val formMargin = "7dlu"
    private val label = FlatLabel()
    private val icon = JLabel()
    private val passwordTextField = OutlinePasswordField()
    private val tip = FlatLabel()
    private val safeBtn = FlatButton()

    var isOpened = false

    init {
        size = Dimension(UIManager.getInt("Dialog.width") - 200, UIManager.getInt("Dialog.height") - 150)
        isModal = true
        isResizable = false
        controlsVisible = false

        if (SystemInfo.isWindows || SystemInfo.isLinux) {
            title = I18n.getString("termora.doorman.safe")
            rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE, false)
        }


        if (SystemInfo.isWindows || SystemInfo.isLinux) {
            val sizes = listOf(16, 20, 24, 28, 32, 48, 64)
            val loader = TermoraFrame::class.java.classLoader
            val images = sizes.mapNotNull { e ->
                loader.getResourceAsStream("icons/termora_${e}x${e}.png")?.use { ImageIO.read(it) }
            }
            iconImages = images
        }

        setLocationRelativeTo(null)
        init()
    }

    override fun createCenterPanel(): JComponent {
        label.text = I18n.getString("termora.doorman.safe")
        tip.text = I18n.getString("termora.doorman.unlock-data")
        icon.icon = FlatSVGIcon(Icons.role.name, 80, 80)
        safeBtn.icon = Icons.unlocked


        label.labelType = FlatLabel.LabelType.h2
        label.horizontalAlignment = SwingConstants.CENTER
        safeBtn.isFocusable = false
        tip.foreground = UIManager.getColor("TextField.placeholderForeground")
        icon.horizontalAlignment = SwingConstants.CENTER


        safeBtn.addActionListener { doOKAction() }
        passwordTextField.addActionListener { doOKAction() }

        var rows = 2
        val step = 2
        return FormBuilder.create().debug(false)
            .layout(
                FormLayout(
                    "$formMargin, default:grow, 4dlu, pref, $formMargin",
                    "${"0dlu"}, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin"
                )
            )
            .add(icon).xyw(2, rows, 4).apply { rows += step }
            .add(label).xyw(2, rows, 4).apply { rows += step }
            .add(passwordTextField).xy(2, rows)
            .add(safeBtn).xy(4, rows).apply { rows += step }
            .add(tip).xyw(2, rows, 4, "center, fill").apply { rows += step }
            .add(JXHyperlink(object : AnAction(I18n.getString("termora.doorman.forget-password")) {
                override fun actionPerformed(evt: AnActionEvent) {
                    val option = OptionPane.showConfirmDialog(
                        this@DoormanDialog, I18n.getString("termora.doorman.forget-password-message"),
                        options = arrayOf(
                            I18n.getString("termora.doorman.have-a-mnemonic"),
                            I18n.getString("termora.doorman.dont-have-a-mnemonic"),
                        ),
                        optionType = JOptionPane.YES_NO_OPTION,
                        messageType = JOptionPane.INFORMATION_MESSAGE,
                        initialValue = I18n.getString("termora.doorman.have-a-mnemonic")
                    )
                    if (option == JOptionPane.YES_OPTION) {
                        showMnemonicsDialog()
                    } else if (option == JOptionPane.NO_OPTION) {
                        OptionPane.showMessageDialog(
                            this@DoormanDialog,
                            I18n.getString("termora.doorman.delete-data"),
                            messageType = JOptionPane.WARNING_MESSAGE
                        )
                        Application.browse(MigrationApplicationRunnerExtension.instance.getDatabaseFile().toURI())
                    }
                }
            }).apply { isFocusable = false }).xyw(2, rows, 4, "center, fill")
            .build()
    }

    private fun showMnemonicsDialog() {
        val dialog = MnemonicsDialog(this@DoormanDialog)
        dialog.isVisible = true
        val entropy = dialog.entropy
        if (entropy.isEmpty()) {
            return
        }

        try {
            val keyBackup = Database.getDatabase()
                .properties.getString("doorman-key-backup")
                ?: throw IllegalStateException("doorman-key-backup is null")
            val key = AES.ECB.decrypt(entropy, keyBackup.decodeBase64())
            Doorman.getInstance().work(key)
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
            OptionPane.showMessageDialog(
                this, I18n.getString("termora.doorman.mnemonic-data-corrupted"),
                messageType = JOptionPane.ERROR_MESSAGE
            )
            passwordTextField.outline = "error"
            passwordTextField.requestFocus()
            return
        }

        isOpened = true
        super.doOKAction()

    }

    override fun doOKAction() {
        if (passwordTextField.password.isEmpty()) {
            passwordTextField.outline = "error"
            passwordTextField.requestFocus()
            return
        }

        try {
            Doorman.getInstance().work(passwordTextField.password)
        } catch (e: Exception) {
            if (e is PasswordWrongException) {
                OptionPane.showMessageDialog(
                    this, I18n.getString("termora.doorman.password-wrong"),
                    messageType = JOptionPane.ERROR_MESSAGE
                )
            }
            passwordTextField.outline = "error"
            passwordTextField.requestFocus()
            return
        }

        isOpened = true

        super.doOKAction()
    }

    fun open(): Boolean {
        isModal = true
        isVisible = true
        return isOpened
    }


    private class MnemonicsDialog(owner: Window) : DialogWrapper(owner) {

        private val textFields = (1..12).map { PasteTextField(it) }
        var entropy = byteArrayOf()
            private set

        init {
            isModal = true
            isResizable = true
            controlsVisible = false
            title = I18n.getString("termora.doorman.mnemonic.title")
            init()
            pack()
            size = Dimension(max(size.width, UIManager.getInt("Dialog.width") - 250), size.height)
            setLocationRelativeTo(null)
        }

        fun getWords(): List<String> {
            val words = mutableListOf<String>()
            for (e in textFields) {
                if (e.text.isBlank()) {
                    return emptyList()
                }
                words.add(e.text)
            }
            return words
        }

        override fun createCenterPanel(): JComponent {
            val formMargin = "4dlu"
            val layout = FormLayout(
                "default:grow, $formMargin, default:grow, $formMargin, default:grow, $formMargin, default:grow",
                "pref, $formMargin, pref, $formMargin, pref"
            )

            val builder = FormBuilder.create().padding("0, $formMargin, $formMargin, $formMargin")
                .layout(layout).debug(false)
            val iterator = textFields.iterator()
            for (i in 1..5 step 2) {
                for (j in 1..7 step 2) {
                    builder.add(iterator.next()).xy(j, i)
                }
            }

            return builder.build()
        }

        override fun doOKAction() {
            for (textField in textFields) {
                if (textField.text.isBlank()) {
                    textField.outline = "error"
                    textField.requestFocusInWindow()
                    return
                }
            }

            try {
                Mnemonics.MnemonicCode(getWords().joinToString(StringUtils.SPACE)).use {
                    it.validate()
                    entropy = it.toEntropy()
                }
            } catch (e: Exception) {
                OptionPane.showMessageDialog(
                    this,
                    I18n.getString("termora.doorman.mnemonic.incorrect"),
                    messageType = JOptionPane.ERROR_MESSAGE
                )
                return
            }


            super.doOKAction()
        }

        override fun doCancelAction() {
            entropy = byteArrayOf()
            super.doCancelAction()
        }

        private inner class PasteTextField(private val index: Int) : OutlineTextField() {
            init {
                addKeyListener(object : KeyAdapter() {
                    override fun keyPressed(e: KeyEvent) {
                        if (e.keyCode == KeyEvent.VK_BACK_SPACE) {
                            if (text.isEmpty() && index != 1) {
                                textFields[index - 2].requestFocusInWindow()
                            }
                        }
                    }
                })
            }

            override fun paste() {
                if (!toolkit.systemClipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    return
                }

                val text = toolkit.systemClipboard.getData(DataFlavor.stringFlavor)?.toString() ?: return
                if (text.isBlank()) {
                    return
                }
                val words = mutableListOf<String>()
                if (text.count { it == ControlCharacters.SP } > text.count { it == ControlCharacters.LF }) {
                    words.addAll(text.split(StringUtils.SPACE))
                } else {
                    words.addAll(text.split(ControlCharacters.LF))
                }
                val iterator = words.iterator()
                for (i in index..textFields.size) {
                    if (iterator.hasNext()) {
                        textFields[i - 1].text = iterator.next()
                        textFields[i - 1].requestFocusInWindow()
                    } else {
                        break
                    }
                }
            }
        }
    }


    override fun createSouthPanel(): JComponent? {
        return null
    }
}