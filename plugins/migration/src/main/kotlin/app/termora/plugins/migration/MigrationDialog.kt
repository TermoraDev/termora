package app.termora.plugins.migration

import app.termora.*
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.util.SystemInfo
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.JXEditorPane
import java.awt.Dimension
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.event.HyperlinkEvent

class MigrationDialog(owner: Window?) : DialogWrapper(owner) {

    private var isOpened = false

    init {
        size = Dimension(UIManager.getInt("Dialog.width") - 200, UIManager.getInt("Dialog.height") - 150)
        isModal = true
        isResizable = false
        controlsVisible = false
        escapeDispose = false

        if (SystemInfo.isWindows || SystemInfo.isLinux) {
            title = StringUtils.EMPTY
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
        var rows = 2
        val step = 2
        val formMargin = "7dlu"
        val icon = JLabel()
        icon.horizontalAlignment = SwingConstants.CENTER
        icon.icon = FlatSVGIcon(Icons.newUI.name, 80, 80)

        val editorPane = JXEditorPane()
        editorPane.contentType = "text/html"
        editorPane.text = MigrationI18n.getString("termora.plugins.migration.message")
        editorPane.isEditable = false
        editorPane.addHyperlinkListener {
            if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                Application.browse(it.url.toURI())
            }
        }
        editorPane.background = DynamicColor("window")
        val scrollPane = JScrollPane(editorPane)
        scrollPane.border = BorderFactory.createEmptyBorder()
        scrollPane.preferredSize = Dimension(Int.MAX_VALUE, 225)

        addWindowListener(object : WindowAdapter() {
            override fun windowOpened(e: WindowEvent) {
                removeWindowListener(this)
                SwingUtilities.invokeLater { scrollPane.verticalScrollBar.value = 0 }
            }
        })

        return FormBuilder.create().debug(false)
            .layout(
                FormLayout(
                    "$formMargin, default:grow, 4dlu, pref, $formMargin",
                    "${"0dlu"}, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin"
                )
            )
            .add(icon).xyw(2, rows, 4).apply { rows += step }
            .add(scrollPane).xyw(2, rows, 4).apply { rows += step }
            .build()
    }


    fun open(): Boolean {
        isModal = true
        isVisible = true
        return isOpened
    }

    override fun doOKAction() {
        isOpened = true
        super.doOKAction()
    }

    override fun doCancelAction() {
        isOpened = false
        super.doCancelAction()
    }

    override fun createOkAction(): AbstractAction {
        return OkAction(MigrationI18n.getString("termora.plugins.migration.migrate"))
    }


}