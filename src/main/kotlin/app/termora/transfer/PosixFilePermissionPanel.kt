package app.termora.transfer

import app.termora.DocumentAdaptor
import app.termora.I18n
import app.termora.OptionsPane.Companion.FORM_MARGIN
import app.termora.OutlineTextField
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import java.awt.BorderLayout
import java.awt.Dimension
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import kotlin.math.max

class PosixFilePermissionPanel(private val permissions: Set<PosixFilePermission>) : JPanel(BorderLayout()) {


    private val ownerRead = JCheckBox(I18n.getString("termora.transport.permissions.read"))
    private val ownerWrite = JCheckBox(I18n.getString("termora.transport.permissions.write"))
    private val ownerExecute = JCheckBox(I18n.getString("termora.transport.permissions.execute"))
    private val groupRead = JCheckBox(I18n.getString("termora.transport.permissions.read"))
    private val groupWrite = JCheckBox(I18n.getString("termora.transport.permissions.write"))
    private val groupExecute = JCheckBox(I18n.getString("termora.transport.permissions.execute"))
    private val otherRead = JCheckBox(I18n.getString("termora.transport.permissions.read"))
    private val otherWrite = JCheckBox(I18n.getString("termora.transport.permissions.write"))
    private val otherExecute = JCheckBox(I18n.getString("termora.transport.permissions.execute"))
    private val includeSubFolder = JCheckBox(I18n.getString("termora.transport.permissions.include-subfolder"))
    private val octalTextField = OutlineTextField()


    init {
        initView()
        initEvents()
    }

    private fun initView() {
        initCheckBox(permissions)

        ownerRead.isFocusable = false
        ownerWrite.isFocusable = false
        ownerExecute.isFocusable = false
        groupRead.isFocusable = false
        groupWrite.isFocusable = false
        groupExecute.isFocusable = false
        otherRead.isFocusable = false
        otherWrite.isFocusable = false
        otherExecute.isFocusable = false
        includeSubFolder.isFocusable = false

        updateOctalMode()

        add(createCenterPanel(), BorderLayout.CENTER)

        preferredSize = Dimension(
            max(preferredSize.width, UIManager.getInt("Dialog.width") - 350),
            preferredSize.height
        )

    }

    private fun initCheckBox(permissions: Set<PosixFilePermission>) {
        ownerRead.isSelected = permissions.contains(PosixFilePermission.OWNER_READ)
        ownerWrite.isSelected = permissions.contains(PosixFilePermission.OWNER_WRITE)
        ownerExecute.isSelected = permissions.contains(PosixFilePermission.OWNER_EXECUTE)
        groupRead.isSelected = permissions.contains(PosixFilePermission.GROUP_READ)
        groupWrite.isSelected = permissions.contains(PosixFilePermission.GROUP_WRITE)
        groupExecute.isSelected = permissions.contains(PosixFilePermission.GROUP_EXECUTE)
        otherRead.isSelected = permissions.contains(PosixFilePermission.OTHERS_READ)
        otherWrite.isSelected = permissions.contains(PosixFilePermission.OTHERS_WRITE)
        otherExecute.isSelected = permissions.contains(PosixFilePermission.OTHERS_EXECUTE)

    }

    private fun initEvents() {
        ownerRead.addActionListener { updateOctalMode() }
        ownerWrite.addActionListener { updateOctalMode() }
        ownerExecute.addActionListener { updateOctalMode() }
        groupRead.addActionListener { updateOctalMode() }
        groupWrite.addActionListener { updateOctalMode() }
        groupExecute.addActionListener { updateOctalMode() }
        otherRead.addActionListener { updateOctalMode() }
        otherWrite.addActionListener { updateOctalMode() }
        otherExecute.addActionListener { updateOctalMode() }

        octalTextField.document.addDocumentListener(object : DocumentAdaptor() {
            override fun changedUpdate(e: DocumentEvent) {
                octalTextField.document.removeDocumentListener(this)
                val text = octalTextField.text.trim()
                val mode = text.toIntOrNull(radix = 8) ?: toOctalMode(getPermissions())
                initCheckBox(fromOctalMode(mode))
                octalTextField.document.addDocumentListener(this)
            }
        })

    }

    private fun updateOctalMode() {
        octalTextField.text = toOctalMode(getPermissions()).toString(8)
    }

    private fun createCenterPanel(): JComponent {
        val formMargin = FORM_MARGIN
        val layout = FormLayout(
            "default:grow, $formMargin, default:grow, $formMargin, default:grow",
            "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
        )

        val builder = FormBuilder.create().layout(layout).debug(false)

        builder.add("${I18n.getString("termora.transport.permissions.file-folder-permissions")}:").xyw(1, 1, 5)

        val ownerBox = Box.createVerticalBox()
        ownerBox.add(ownerRead)
        ownerBox.add(ownerWrite)
        ownerBox.add(ownerExecute)
        ownerBox.border = BorderFactory.createTitledBorder(I18n.getString("termora.transport.permissions.owner"))
        builder.add(ownerBox).xy(1, 3)

        val groupBox = Box.createVerticalBox()
        groupBox.add(groupRead)
        groupBox.add(groupWrite)
        groupBox.add(groupExecute)
        groupBox.border = BorderFactory.createTitledBorder(I18n.getString("termora.transport.permissions.group"))
        builder.add(groupBox).xy(3, 3)

        val otherBox = Box.createVerticalBox()
        otherBox.add(otherRead)
        otherBox.add(otherWrite)
        otherBox.add(otherExecute)
        otherBox.border = BorderFactory.createTitledBorder(I18n.getString("termora.transport.permissions.others"))
        builder.add(otherBox).xy(5, 3)

        val box = Box.createHorizontalBox()
        box.add(JLabel(I18n.getString("termora.transport.permissions.octal-mode") + ": "))
        box.add(octalTextField)
        builder.add(box).xyw(1, 5, 5)

        builder.add(includeSubFolder).xyw(1, 7, 5)

        return builder.build()
    }


    fun isIncludeSubdirectories(): Boolean {
        return includeSubFolder.isSelected
    }

    fun toOctalMode(p: Set<PosixFilePermission>): Int {
        var mode = 0
        if (p.contains(PosixFilePermission.OWNER_READ)) mode = mode or 256
        if (p.contains(PosixFilePermission.OWNER_WRITE)) mode = mode or 128
        if (p.contains(PosixFilePermission.OWNER_EXECUTE)) mode = mode or 64
        if (p.contains(PosixFilePermission.GROUP_READ)) mode = mode or 32
        if (p.contains(PosixFilePermission.GROUP_WRITE)) mode = mode or 16
        if (p.contains(PosixFilePermission.GROUP_EXECUTE)) mode = mode or 8
        if (p.contains(PosixFilePermission.OTHERS_READ)) mode = mode or 4
        if (p.contains(PosixFilePermission.OTHERS_WRITE)) mode = mode or 2
        if (p.contains(PosixFilePermission.OTHERS_EXECUTE)) mode = mode or 1
        return mode
    }

    fun fromOctalMode(mode: Int): Set<PosixFilePermission> {
        val m = mode and 511 // 0777 == 511
        val set = EnumSet.noneOf(PosixFilePermission::class.java)
        if ((m and 256) != 0) set.add(PosixFilePermission.OWNER_READ)
        if ((m and 128) != 0) set.add(PosixFilePermission.OWNER_WRITE)
        if ((m and 64) != 0) set.add(PosixFilePermission.OWNER_EXECUTE)
        if ((m and 32) != 0) set.add(PosixFilePermission.GROUP_READ)
        if ((m and 16) != 0) set.add(PosixFilePermission.GROUP_WRITE)
        if ((m and 8) != 0) set.add(PosixFilePermission.GROUP_EXECUTE)
        if ((m and 4) != 0) set.add(PosixFilePermission.OTHERS_READ)
        if ((m and 2) != 0) set.add(PosixFilePermission.OTHERS_WRITE)
        if ((m and 1) != 0) set.add(PosixFilePermission.OTHERS_EXECUTE)
        return set
    }


    fun getPermissions(): Set<PosixFilePermission> {

        val permissions = mutableSetOf<PosixFilePermission>()
        if (ownerRead.isSelected) {
            permissions.add(PosixFilePermission.OWNER_READ)
        }
        if (ownerWrite.isSelected) {
            permissions.add(PosixFilePermission.OWNER_WRITE)
        }
        if (ownerExecute.isSelected) {
            permissions.add(PosixFilePermission.OWNER_EXECUTE)
        }
        if (groupRead.isSelected) {
            permissions.add(PosixFilePermission.GROUP_READ)
        }
        if (groupWrite.isSelected) {
            permissions.add(PosixFilePermission.GROUP_WRITE)
        }
        if (groupExecute.isSelected) {
            permissions.add(PosixFilePermission.GROUP_EXECUTE)
        }
        if (otherRead.isSelected) {
            permissions.add(PosixFilePermission.OTHERS_READ)
        }
        if (otherWrite.isSelected) {
            permissions.add(PosixFilePermission.OTHERS_WRITE)
        }
        if (otherExecute.isSelected) {
            permissions.add(PosixFilePermission.OTHERS_EXECUTE)
        }

        return permissions
    }
}