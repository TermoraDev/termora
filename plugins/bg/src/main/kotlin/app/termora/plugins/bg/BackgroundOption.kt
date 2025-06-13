package app.termora.plugins.bg

import app.termora.*
import app.termora.OptionsPane.Companion.FORM_MARGIN
import app.termora.database.DatabaseManager
import app.termora.nv.FileChooser
import com.formdev.flatlaf.extras.components.FlatButton
import com.formdev.flatlaf.extras.components.FlatTextPane
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.io.File
import java.nio.file.StandardCopyOption
import javax.swing.*
import javax.swing.event.DocumentEvent

class BackgroundOption : JPanel(BorderLayout()), OptionsPane.PluginOption {
    companion object {
        private val log = LoggerFactory.getLogger(BackgroundOption::class.java)
    }

    private val owner get() = SwingUtilities.getWindowAncestor(this)

    val backgroundImageTextField = OutlineTextField()
    val intervalSpinner = NumberSpinner(360, minimum = 30, maximum = 86400)

    private val backgroundButton = JButton(Icons.folder)
    private val backgroundClearButton = FlatButton()


    init {
        initView()
        initEvents()
    }

    private fun initView() {

        backgroundImageTextField.isEditable = false
        backgroundImageTextField.trailingComponent = backgroundButton
        backgroundImageTextField.text = Appearance.backgroundImage
        backgroundImageTextField.document.addDocumentListener(object : DocumentAdaptor() {
            override fun changedUpdate(e: DocumentEvent) {
                backgroundClearButton.isEnabled = backgroundImageTextField.text.isNotBlank()
            }
        })

        backgroundClearButton.isFocusable = false
        backgroundClearButton.isEnabled = backgroundImageTextField.text.isNotBlank()
        backgroundClearButton.icon = Icons.delete
        backgroundClearButton.buttonType = FlatButton.ButtonType.toolBarButton

        intervalSpinner.value = Appearance.interval

        add(getFormPanel(), BorderLayout.CENTER)
    }

    private fun initEvents() {
        backgroundButton.addActionListener {
            val chooser = FileChooser()
            chooser.osxAllowedFileTypes = listOf("png", "jpg", "jpeg")
            chooser.allowsMultiSelection = false
            chooser.win32Filters.add(Pair("Image files", listOf("png", "jpg", "jpeg")))
            chooser.fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
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

        intervalSpinner.addChangeListener {
            val value = intervalSpinner.value
            if (value is Int) {
                Appearance.interval = value
            }
        }
    }

    private fun onSelectedBackgroundImage(file: File) {
        try {
            if (file.isFile) {
                val destFile = FileUtils.getFile(Application.getBaseDataDir(), "background", file.name)
                FileUtils.forceMkdirParent(destFile)
                FileUtils.deleteQuietly(destFile)
                FileUtils.copyFile(file, destFile, StandardCopyOption.REPLACE_EXISTING)
                BackgroundManager.getInstance().setBackgroundImage(destFile.absolutePath)
            } else if (file.isDirectory) {
                BackgroundManager.getInstance().setBackgroundImage(file.absolutePath)
            }
            backgroundImageTextField.text = file.absolutePath
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
        return Icons.imageGray
    }

    override fun getTitle(): String {
        return BGI18n.getString("termora.plugins.bg.background-image")
    }

    override fun getJComponent(): JComponent {
        return this
    }


    private fun getFormPanel(): JPanel {
        val layout = FormLayout(
            "left:pref, $FORM_MARGIN, default:grow, $FORM_MARGIN, default",
            "pref, $FORM_MARGIN, pref"
        )

        var rows = 1
        val step = 2
        val builder = FormBuilder.create().layout(layout)
        val bgClearBox = Box.createHorizontalBox()
        bgClearBox.add(backgroundClearButton)

        builder.add("${BGI18n.getString("termora.plugins.bg.background-image")}:").xy(1, rows)
            .add(backgroundImageTextField).xy(3, rows)
            .add(bgClearBox).xy(5, rows)
            .apply { rows += step }

        builder.add("${BGI18n.getString("termora.plugins.bg.interval")}:").xy(1, rows)
            .add(intervalSpinner).xy(3, rows)
            .apply { rows += step }


        return builder.build()
    }

}