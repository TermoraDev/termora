package app.termora.plugins.migration

import app.termora.*
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.util.SystemInfo
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.JXEditorPane
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.event.HyperlinkEvent

class MigrationDialog(owner: Window?) : DialogWrapper(owner) {
    companion object {
        private val log = LoggerFactory.getLogger(MigrationDialog::class.java)
    }

    private var isOpened = false

    init {
        size = Dimension(UIManager.getInt("Dialog.width") - 200, UIManager.getInt("Dialog.height") - 150)
        isModal = true
        isResizable = false
        controlsVisible = false

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
        editorPane.text = """
            <html>
              <h1 align="center">2.0 已就绪。</h1>
              <br/>
              <h3>1. 存储结构已更新，需迁移现有数据。只需点击 <font color="#3573F0">“迁移”</font> 即可完成操作。</h3>
              <h3>2. <font color="#3573F0">同步功能</font> 现作为插件提供，如需使用，请前往设置中 <font color="#EA33EC">手动安装</font>。</h3>
              <h3>3. <font color="#3573F0">数据加密</font> 功能已被 <font color="#EA33EC">移除</font>（本地数据将以简单加密方式存储），请确保你的设备处于可信环境中。</h3>
              <h3 align="center">📎 更多信息请查看：<a href="https://github.com/TermoraDev/termora/issues/645">TermoraDev/termora#593</a></h3>
            </html>
        """.trimIndent()
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
        return OkAction("迁移")
    }


}