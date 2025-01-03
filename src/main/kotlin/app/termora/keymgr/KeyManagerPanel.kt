package app.termora.keymgr

import app.termora.*
import app.termora.AES.decodeBase64
import app.termora.native.FileChooser
import com.formdev.flatlaf.extras.components.FlatComboBox
import com.formdev.flatlaf.extras.components.FlatTable
import com.formdev.flatlaf.extras.components.FlatTextArea
import com.formdev.flatlaf.icons.FlatFileViewFileIcon
import com.formdev.flatlaf.ui.FlatTextBorder
import com.formdev.flatlaf.util.SystemInfo
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import net.i2p.crypto.eddsa.EdDSAPublicKey
import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.IOUtils
import org.apache.commons.io.file.PathUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.time.DateFormatUtils
import org.apache.sshd.common.config.keys.FilePasswordProvider
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter
import org.apache.sshd.common.keyprovider.FileKeyPairProvider
import org.apache.sshd.common.keyprovider.KeyPairProvider
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Window
import java.awt.event.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.KeyPair
import java.security.spec.X509EncodedKeySpec
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.swing.*
import javax.swing.border.EmptyBorder
import kotlin.io.path.writeText

class KeyManagerPanel : JPanel(BorderLayout()) {
    val keyPairTable = FlatTable()
    val keyPairTableModel = KeyPairTableModel()

    private val generateBtn = JButton(I18n.getString("termora.keymgr.generate"))
    private val importBtn = JButton(I18n.getString("termora.keymgr.import"))
    private val exportBtn = JButton(I18n.getString("termora.keymgr.export"))
    private val editBtn = JButton(I18n.getString("termora.keymgr.edit"))
    private val deleteBtn = JButton(I18n.getString("termora.remove"))

    init {
        initView()
        initEvents()
    }


    private fun initView() {

        exportBtn.isEnabled = false
        editBtn.isEnabled = false
        deleteBtn.isEnabled = false

        keyPairTableModel.addColumn(I18n.getString("termora.keymgr.table.name"))
        keyPairTableModel.addColumn(I18n.getString("termora.keymgr.table.type"))
        keyPairTableModel.addColumn(I18n.getString("termora.keymgr.table.length"))
        keyPairTableModel.addColumn(I18n.getString("termora.keymgr.table.remark"))
        keyPairTable.model = keyPairTableModel
        keyPairTable.fillsViewportHeight = true

        KeyManager.instance.getOhKeyPairs().forEach {
            keyPairTableModel.addRow(arrayOf(it))
        }

        val formMargin = "4dlu"
        val layout = FormLayout(
            "default:grow",
            "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin"
        )

        var rows = 1
        val step = 2

        add(JScrollPane(keyPairTable).apply {
            border = BorderFactory.createMatteBorder(1, 1, 1, 1, DynamicColor.BorderColor)
        }, BorderLayout.CENTER)
        add(
            FormBuilder.create().layout(layout).padding(EmptyBorder(0, 12, 0, 0))
                .add(generateBtn).xy(1, rows).apply { rows += step }
                .add(editBtn).xy(1, rows).apply { rows += step }
                .add(importBtn).xy(1, rows).apply { rows += step }
                .add(exportBtn).xy(1, rows).apply { rows += step }
                .add(deleteBtn).xy(1, rows).apply { rows += step }
                .build(), BorderLayout.EAST)
        border = BorderFactory.createEmptyBorder(if (SystemInfo.isWindows || SystemInfo.isLinux) 6 else 0, 12, 12, 12)

    }

    private fun initEvents() {
        generateBtn.addActionListener {
            val dialog = GenerateKeyDialog(SwingUtilities.getWindowAncestor(this))
            dialog.isVisible = true
            if (dialog.ohKeyPair != OhKeyPair.empty) {
                val keyPair = dialog.ohKeyPair
                KeyManager.instance.addOhKeyPair(keyPair)
                keyPairTableModel.addRow(arrayOf(keyPair))
            }
        }

        deleteBtn.addActionListener {
            if (keyPairTable.selectedRowCount > 0) {
                if (OptionPane.showConfirmDialog(
                        SwingUtilities.getWindowAncestor(this),
                        I18n.getString("termora.keymgr.delete-warning"),
                        messageType = JOptionPane.WARNING_MESSAGE
                    ) == JOptionPane.YES_OPTION
                ) {
                    val rows = keyPairTable.selectedRows.sorted().reversed()
                    for (row in rows) {
                        val id = keyPairTableModel.getOhKeyPair(row).id
                        KeyManager.instance.removeOhKeyPair(id)
                        keyPairTableModel.removeRow(row)
                    }
                }
            }
        }

        importBtn.addActionListener {
            val dialog = ImportKeyDialog(SwingUtilities.getWindowAncestor(this))
            dialog.isVisible = true
            if (dialog.ohKeyPair != OhKeyPair.empty) {
                KeyManager.instance.addOhKeyPair(dialog.ohKeyPair)
                keyPairTableModel.addRow(arrayOf(dialog.ohKeyPair))
            }
        }

        editBtn.addActionListener {
            val row = keyPairTable.selectedRow
            if (row >= 0) {
                var ohKeyPair = keyPairTableModel.getOhKeyPair(row)
                val dialog = GenerateKeyDialog(
                    SwingUtilities.getWindowAncestor(this),
                    ohKeyPair,
                    true
                )
                dialog.title = ohKeyPair.name
                dialog.isVisible = true
                ohKeyPair = dialog.ohKeyPair

                if (ohKeyPair != OhKeyPair.empty) {
                    KeyManager.instance.addOhKeyPair(ohKeyPair)
                    keyPairTableModel.setValueAt(ohKeyPair, row, 0)
                    keyPairTableModel.fireTableRowsUpdated(row, row)
                }
            }
        }

        exportBtn.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val keyPairs = keyPairTable.selectedRows.map { keyPairTableModel.getOhKeyPair(it) }
                if (keyPairs.isEmpty()) {
                    return
                }

                val fileChooser = FileChooser()
                fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
                fileChooser.win32Filters.add(Pair("Zip files", listOf("zip")))
                fileChooser.showSaveDialog(SwingUtilities.getWindowAncestor(this@KeyManagerPanel), "key-export.zip")
                    .thenAccept { file ->
                        if (file != null) {
                            SwingUtilities.invokeLater { exportKeyPairs(file, keyPairs) }
                        }
                    }

            }
        })

        keyPairTable.selectionModel.addListSelectionListener {
            exportBtn.isEnabled = keyPairTable.selectedRowCount > 0
            editBtn.isEnabled = exportBtn.isEnabled
            deleteBtn.isEnabled = exportBtn.isEnabled
        }
    }

    private fun exportKeyPairs(file: File, keyPairs: List<OhKeyPair>) {
        file.outputStream().use { fis ->
            val names = mutableMapOf<String, Int>()
            ZipOutputStream(fis).use { zos ->
                for (keyPair in keyPairs) {
                    val pubNameCount = names.getOrPut(keyPair.name + ".pub") { 0 }
                    val priNameCount = names.getOrPut(keyPair.name) { 0 }
                    val kp = OhKeyPairKeyPairProvider.generateKeyPair(keyPair)
                    val publicKey = kp.public
                    val privateKey = kp.private

                    zos.putNextEntry(ZipEntry("${keyPair.name}${if (pubNameCount > 0) ".${pubNameCount}" else String()}.pub"))
                    OpenSSHKeyPairResourceWriter.INSTANCE.writePublicKey(publicKey, null, zos)
                    zos.closeEntry()

                    zos.putNextEntry(ZipEntry("${keyPair.name}${if (priNameCount > 0) ".${priNameCount}" else String()}"))
                    OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(
                        KeyPair(publicKey, privateKey),
                        null,
                        null,
                        zos
                    )
                    zos.closeEntry()


                    names[keyPair.name + ".pub"] = pubNameCount + 1
                    names[keyPair.name] = priNameCount + 1
                }
            }
        }

        OptionPane.openFileInFolder(
            SwingUtilities.getWindowAncestor(this),
            file, I18n.getString("termora.settings.sync.export-done-open-folder"),
            I18n.getString("termora.settings.sync.export-done")
        )
    }

    private class GenerateKeyDialog(
        owner: Window,
        var ohKeyPair: OhKeyPair = OhKeyPair.empty,
        val editable: Boolean = false,
    ) : DialogWrapper(owner) {
        private val formMargin = "7dlu"
        private val typeComboBox = FlatComboBox<String>()
        private val lengthComboBox = FlatComboBox<Int>()
        private val nameTextField = OutlineTextField(32)
        private val remarkTextField = OutlineTextField()
        private val publicKeyTextArea = FlatTextArea()
        private val savePublicKeyBtn = JButton(I18n.getString("termora.save"))
        private val okAction = super.createOkAction()

        init {
            isModal = true
            title = I18n.getString("termora.keymgr.title")

            typeComboBox.addItem("RSA")
            typeComboBox.addItem("ED25519")

            // 默认 RSA
            lengthComboBox.addItem(1024)
            lengthComboBox.addItem(1024 * 2)
            lengthComboBox.addItem(1024 * 3)
            lengthComboBox.addItem(1024 * 4)
            lengthComboBox.addItem(1024 * 8)
            lengthComboBox.selectedItem = 1024 * 2

            nameTextField.text = "${typeComboBox.selectedItem}_${lengthComboBox.selectedItem}"

            publicKeyTextArea.rows = 6
            publicKeyTextArea.lineWrap = true
            publicKeyTextArea.wrapStyleWord = false
            publicKeyTextArea.isEditable = false
            publicKeyTextArea.autoscrolls = false
            publicKeyTextArea.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            savePublicKeyBtn.isEnabled = false


            initEvents()

            if (editable) {
                typeComboBox.isEnabled = false
                lengthComboBox.isEnabled = false
                typeComboBox.selectedItem = ohKeyPair.type
                lengthComboBox.selectedItem = ohKeyPair.length
                nameTextField.text = ohKeyPair.name
                remarkTextField.text = ohKeyPair.remark
                val baos = ByteArrayOutputStream()
                if (ohKeyPair.type == "RSA") {
                    OpenSSHKeyPairResourceWriter.INSTANCE
                        .writePublicKey(RSA.generatePublic(ohKeyPair.publicKey.decodeBase64()), null, baos)
                } else if (ohKeyPair.type == "ED25519") {
                    OpenSSHKeyPairResourceWriter.INSTANCE.writePublicKey(
                        EdDSAPublicKey(X509EncodedKeySpec(ohKeyPair.publicKey.decodeBase64())),
                        null, baos
                    )
                }
                publicKeyTextArea.text = baos.toString()
                savePublicKeyBtn.isEnabled = true
            } else {
                val itemListener = ItemListener { e ->
                    if (e.stateChange == ItemEvent.SELECTED) {
                        nameTextField.text = "${typeComboBox.selectedItem}_${lengthComboBox.selectedItem}"
                    }
                }
                typeComboBox.addItemListener(itemListener)
                lengthComboBox.addItemListener(itemListener)
                nameTextField.addKeyListener(object : KeyAdapter() {
                    override fun keyTyped(e: KeyEvent) {
                        if (Character.isDefined(e.keyChar)) {
                            typeComboBox.removeItemListener(itemListener)
                            lengthComboBox.removeItemListener(itemListener)
                        }
                    }
                })
            }

            init()

            pack()
            size = Dimension(UIManager.getInt("Dialog.width") - 300, size.height)
            setLocationRelativeTo(null)

        }

        override fun createCenterPanel(): JComponent {
            val layout = FormLayout(
                "left:pref, $formMargin, default:grow",
                "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
            )

            var rows = 1
            val step = 2
            return FormBuilder.create().layout(layout).padding("0dlu, $formMargin, $formMargin, $formMargin")
                .add("${I18n.getString("termora.keymgr.table.type")}:").xy(1, rows)
                .add(typeComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.keymgr.table.length")}:").xy(1, rows)
                .add(lengthComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.keymgr.table.name")}:").xy(1, rows)
                .add(nameTextField).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.keymgr.table.remark")}:").xy(1, rows)
                .add(remarkTextField).xy(3, rows).apply { rows += step }
                .add("PublicKey:").xy(1, rows)
                .add(JScrollPane(publicKeyTextArea).apply { border = FlatTextBorder() }).xy(3, rows)
                .apply { rows += step }
                .add(savePublicKeyBtn).xyw(1, rows, 3, "right, fill").apply { rows += step }
                .build()
        }

        private fun initEvents() {

            savePublicKeyBtn.addActionListener {
                val fileChooser = FileChooser()
                fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
                fileChooser.win32Filters.add(Pair("All Files", listOf("*")))
                fileChooser.showSaveDialog(this, "${nameTextField.text}.pub").thenAccept { file ->
                    file?.outputStream()?.use {
                        IOUtils.write(publicKeyTextArea.text, it, StandardCharsets.UTF_8)
                    }
                }
            }

            typeComboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    lengthComboBox.removeAllItems()
                    if (typeComboBox.selectedItem == "ED25519") {
                        lengthComboBox.addItem(256)
                    } else if (typeComboBox.selectedItem == "RSA") {
                        lengthComboBox.addItem(1024)
                        lengthComboBox.addItem(1024 * 2)
                        lengthComboBox.addItem(1024 * 3)
                        lengthComboBox.addItem(1024 * 4)
                        lengthComboBox.addItem(1024 * 8)
                        lengthComboBox.selectedItem = 1024 * 2
                    }
                }
            }
        }

        override fun createOkAction(): AbstractAction {
            if (!editable) {
                okAction.putValue(Action.NAME, I18n.getString("termora.keymgr.generate"))
            }
            return okAction
        }

        override fun doCancelAction() {
            ohKeyPair = OhKeyPair.empty
            super.doCancelAction()
        }

        override fun doOKAction() {

            if (ohKeyPair == OhKeyPair.empty) {
                if (nameTextField.text.isBlank()) {
                    nameTextField.outline = "error"
                    nameTextField.requestFocusInWindow()
                    return
                }

                val keyType = if (typeComboBox.selectedItem == "RSA")
                    KeyPairProvider.SSH_RSA else KeyPairProvider.SSH_ED25519
                val keyPair = KeyUtils.generateKeyPair(keyType, lengthComboBox.selectedItem as Int)
                ohKeyPair = OhKeyPair(
                    id = UUID.randomUUID().toSimpleString(),
                    name = nameTextField.text,
                    remark = remarkTextField.text,
                    type = typeComboBox.selectedItem as String,
                    length = lengthComboBox.selectedItem as Int,
                    publicKey = Base64.encodeBase64String(keyPair.public.encoded),
                    privateKey = Base64.encodeBase64String(keyPair.private.encoded),
                    sort = System.currentTimeMillis()
                )

                val baos = ByteArrayOutputStream()
                OpenSSHKeyPairResourceWriter.INSTANCE
                    .writePublicKey(keyPair.public, null, baos)

                savePublicKeyBtn.isEnabled = true
                publicKeyTextArea.text = baos.toString()
                lengthComboBox.isEnabled = false
                typeComboBox.isEnabled = false



                okAction.putValue(Action.NAME, I18n.getString("termora.confirm"))
            } else {
                ohKeyPair = ohKeyPair.copy(
                    name = nameTextField.text,
                    remark = remarkTextField.text,
                )

                if (!editable && ohKeyPair.remark.isEmpty()) {
                    ohKeyPair = ohKeyPair.copy(
                        remark = "Create on " + DateFormatUtils.format(Date(), I18n.getString("termora.date-format")),
                    )
                }

                super.doOKAction()
            }
        }
    }


    private class ImportKeyDialog(owner: Window) : DialogWrapper(owner) {
        private val formMargin = "7dlu"
        private val fileTextField = OutlineTextField()
        private val typeComboBox = FlatComboBox<String>()
        private val lengthComboBox = FlatComboBox<Int>()
        private val nameTextField = OutlineTextField(32)
        private val remarkTextField = OutlineTextField()
        private val okAction = super.createOkAction()
        private val fileBtn = JButton(FlatFileViewFileIcon())
        private val textBtn = JButton(Icons.dbPrimitive)

        var ohKeyPair = OhKeyPair.empty

        init {
            size = Dimension(UIManager.getInt("Dialog.width") - 300, UIManager.getInt("Dialog.height") - 200)
            isModal = true
            title = I18n.getString("termora.keymgr.import")
            setLocationRelativeTo(null)

            typeComboBox.isEnabled = false
            lengthComboBox.isEnabled = false
            fileTextField.isEditable = false

            init()

            pack()
            size = Dimension(UIManager.getInt("Dialog.width") - 300, size.height)
            setLocationRelativeTo(null)

        }

        override fun createCenterPanel(): JComponent {
            val layout = FormLayout(
                "left:pref, $formMargin, default:grow",
                "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin"
            )

            fileBtn.addActionListener { import() }
            textBtn.addActionListener { importText() }
            val box = JToolBar()
            box.add(textBtn)
            box.add(Box.createHorizontalStrut(2))
            box.add(fileBtn)
            fileTextField.trailingComponent = box

            var rows = 1
            val step = 2
            return FormBuilder.create().layout(layout).padding("0dlu, $formMargin, $formMargin, $formMargin")
                .add("File:").xy(1, rows)
                .add(fileTextField).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.keymgr.table.type")}:").xy(1, rows)
                .add(typeComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.keymgr.table.length")}:").xy(1, rows)
                .add(lengthComboBox).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.keymgr.table.name")}:").xy(1, rows)
                .add(nameTextField).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.keymgr.table.remark")}:").xy(1, rows)
                .add(remarkTextField).xy(3, rows).apply { rows += step }
                .build()
        }

        private fun import() {
            val fileChooser = FileChooser()
            fileChooser.allowsMultiSelection = false
            fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
            fileChooser.showOpenDialog(this).thenAccept { files ->
                if (files.isNotEmpty()) {
                    SwingUtilities.invokeLater {
                        importKeyFile(files.first())
                    }
                }
            }
        }

        private fun importText() {
            val textarea = FlatTextArea()
            textarea.rows = 20
            textarea.wrapStyleWord = false
            textarea.lineWrap = true
            textarea.columns = 40
            if (OptionPane.showConfirmDialog(
                    this, object : JScrollPane(textarea) {
                        override fun requestFocusInWindow(): Boolean {
                            return textarea.requestFocusInWindow()
                        }
                    },
                    messageType = JOptionPane.PLAIN_MESSAGE,
                    title = "Private Key",
                    optionType = JOptionPane.OK_CANCEL_OPTION
                ) != JOptionPane.OK_OPTION
            ) {
                return
            }

            if (textarea.text.isBlank()) {
                return
            }

            val tmpFile = Files.createTempFile("${Application.getName()}-", String())
            tmpFile.writeText(textarea.text)

            try {
                importKeyFile(tmpFile.toFile())
            } finally {
                PathUtils.deleteFile(tmpFile)
            }
        }

        private fun importKeyFile(file: File) {
            val length = file.length()
            if (file.isDirectory || length < 1 || length >= 1024 * 1024) {
                OptionPane.showMessageDialog(
                    this,
                    I18n.getString("termora.keymgr.import.error"),
                    messageType = JOptionPane.ERROR_MESSAGE
                )
                return
            }

            try {
                val provider = FileKeyPairProvider(file.toPath())
                provider.passwordFinder = FilePasswordProvider { _, _, _ ->
                    val dialog = InputDialog(owner = this@ImportKeyDialog, title = "Password")
                    dialog.getText() ?: String()
                }
                val keyPair = provider.loadKeys(null).firstOrNull()
                    ?: throw IllegalStateException("Failed to load the key file")
                val keyType = KeyUtils.getKeyType(keyPair)
                if (keyType != KeyPairProvider.SSH_RSA && keyType != KeyPairProvider.SSH_ED25519) {
                    throw UnsupportedOperationException("Key type:${keyType}. Only RSA/ED25519 keys are supported.")
                }

                nameTextField.text = StringUtils.defaultIfBlank(nameTextField.text, file.name)
                fileTextField.text = file.absolutePath
                if (keyType == KeyPairProvider.SSH_RSA) {
                    typeComboBox.addItem("RSA")
                } else {
                    typeComboBox.addItem("ED25519")
                }
                lengthComboBox.addItem(KeyUtils.getKeySize(keyPair.private))

                ohKeyPair = OhKeyPair(
                    name = nameTextField.text,
                    remark = remarkTextField.text,
                    privateKey = Base64.encodeBase64String(keyPair.private.encoded),
                    publicKey = Base64.encodeBase64String(keyPair.public.encoded),
                    sort = System.currentTimeMillis(),
                    type = typeComboBox.selectedItem as String,
                    id = UUID.randomUUID().toSimpleString(),
                    length = lengthComboBox.selectedItem as Int,
                )
            } catch (e: Exception) {
                OptionPane.showMessageDialog(
                    this,
                    e.message ?: e.toString(),
                    messageType = JOptionPane.ERROR_MESSAGE
                )
            }
        }

        override fun createOkAction(): AbstractAction {
            okAction.putValue(Action.NAME, I18n.getString("termora.keymgr.import"))
            return okAction
        }

        override fun doCancelAction() {
            ohKeyPair = OhKeyPair.empty
            super.doCancelAction()
        }

        override fun doOKAction() {


            if (ohKeyPair == OhKeyPair.empty) {
                import()
                return
            }

            if (nameTextField.text.isBlank()) {
                nameTextField.outline = "error"
                nameTextField.requestFocusInWindow()
                return
            }

            if (ohKeyPair.remark.isEmpty()) {
                ohKeyPair = ohKeyPair.copy(
                    name = nameTextField.text,
                    remark = "Import on " + DateFormatUtils.format(Date(), I18n.getString("termora.date-format"))
                )
            }

            super.doOKAction()
        }
    }
}