package app.termora.highlight

import app.termora.DialogWrapper
import app.termora.DynamicColor
import app.termora.I18n
import app.termora.Icons
import app.termora.database.DatabaseManager
import app.termora.terminal.ColorPalette
import app.termora.terminal.TerminalColor
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatTextField
import com.formdev.flatlaf.extras.components.FlatToolBar
import com.formdev.flatlaf.ui.FlatLineBorder
import com.formdev.flatlaf.util.SystemInfo
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*
import kotlin.math.max

class NewKeywordHighlightDialog(
    owner: Window,
    val colorPalette: ColorPalette
) : DialogWrapper(owner) {
    private val formMargin = "7dlu"
    private val keywordHighlightView by lazy { KeywordHighlightView(fontSize = DatabaseManager.getInstance().terminal.fontSize) }

    val keywordTextField = FlatTextField()
    val descriptionTextField = FlatTextField()
    val boldCheckBox = JCheckBox(I18n.getString("termora.highlight.bold"))
    val italicCheckBox = JCheckBox(I18n.getString("termora.highlight.italic"))
    val underlineCheckBox = JCheckBox(I18n.getString("termora.highlight.underline"))
    val lineThroughCheckBox = JCheckBox(I18n.getString("termora.highlight.line-through"))
    val textColor = createColorPanel(
        Color(colorPalette.getColor(TerminalColor.Basic.FOREGROUND)),
        I18n.getString("termora.highlight.text-color")
    )
    val backgroundColor = createColorPanel(
        Color(colorPalette.getColor(TerminalColor.Basic.BACKGROUND)),
        I18n.getString("termora.highlight.background-color")
    )
    val matchCaseBtn = JToggleButton(Icons.matchCase).apply { toolTipText = I18n.getString("termora.match-case") }
    val regexBtn = JToggleButton(Icons.regex).apply { toolTipText = I18n.getString("termora.regex") }


    private val textColorRevert = JButton(Icons.revert)
    private val backgroundColorRevert = JButton(Icons.revert)

    var keywordHighlight: KeywordHighlight? = null

    init {
        isModal = true
        title = I18n.getString("termora.highlight")
        isResizable = false
        controlsVisible = false

        boldCheckBox.horizontalAlignment = SwingConstants.CENTER
        italicCheckBox.horizontalAlignment = SwingConstants.CENTER
        underlineCheckBox.horizontalAlignment = SwingConstants.CENTER
        lineThroughCheckBox.horizontalAlignment = SwingConstants.CENTER
        keywordHighlightView.preferredSize = Dimension(-1, 100)

        textColorRevert.isFocusable = false
        textColorRevert.isEnabled = false
        textColorRevert.toolTipText = I18n.getString("termora.highlight.use-terminal-foreground")
        textColorRevert.putClientProperty(
            FlatClientProperties.BUTTON_TYPE,
            FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON
        )
        backgroundColorRevert.isFocusable = false
        backgroundColorRevert.isEnabled = false
        backgroundColorRevert.toolTipText = I18n.getString("termora.highlight.use-terminal-background")
        backgroundColorRevert.putClientProperty(
            FlatClientProperties.BUTTON_TYPE,
            FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON
        )

        matchCaseBtn.toolTipText = I18n.getString("termora.match-case")


        val box = FlatToolBar()
        box.add(matchCaseBtn)
        box.add(regexBtn)
        keywordTextField.trailingComponent = box

        repaintKeywordHighlightView()

        initEvents()

        init()
        pack()
        size = Dimension(UIManager.getInt("Dialog.width") - 200, max(height, preferredSize.height))
        setLocationRelativeTo(null)

    }

    private fun initEvents() {

        textColor.addPropertyChangeListener("color") {
            repaintKeywordHighlightView()
            textColorRevert.isEnabled = it.newValue != Color(colorPalette.getColor(TerminalColor.Basic.FOREGROUND))
        }
        backgroundColor.addPropertyChangeListener("color") {
            repaintKeywordHighlightView()
            backgroundColorRevert.isEnabled = it.newValue != Color(
                colorPalette.getColor(
                    TerminalColor.Basic.BACKGROUND
                )
            )
        }

        boldCheckBox.addActionListener { repaintKeywordHighlightView() }
        italicCheckBox.addActionListener { repaintKeywordHighlightView() }
        underlineCheckBox.addActionListener { repaintKeywordHighlightView() }
        lineThroughCheckBox.addActionListener { repaintKeywordHighlightView() }

        textColorRevert.addActionListener {
            textColor.color = null
            textColor.background = Color(colorPalette.getColor(TerminalColor.Basic.FOREGROUND))
            textColor.colorIndex = -1
            repaintKeywordHighlightView()
        }
        backgroundColorRevert.addActionListener {
            backgroundColor.color = null
            backgroundColor.background = Color(colorPalette.getColor(TerminalColor.Basic.BACKGROUND))
            backgroundColor.colorIndex = -1
            repaintKeywordHighlightView()
        }


        addWindowListener(object : WindowAdapter() {
            override fun windowActivated(e: WindowEvent) {
                removeWindowListener(this)
                repaintKeywordHighlightView()
            }
        })
    }

    private fun repaintKeywordHighlightView() {
        keywordHighlightView.bold = boldCheckBox.isSelected
        keywordHighlightView.italic = italicCheckBox.isSelected
        keywordHighlightView.underline = underlineCheckBox.isSelected
        keywordHighlightView.lineThrough = lineThroughCheckBox.isSelected

        if (textColor.color == null && textColor.colorIndex == -1) {
            keywordHighlightView.textColor = Color(colorPalette.getColor(TerminalColor.Basic.FOREGROUND))
        } else if (textColor.color != null) {
            keywordHighlightView.textColor = textColor.color
        } else {
            keywordHighlightView.textColor = Color(colorPalette.getXTerm256Color(textColor.colorIndex))
        }

        if (backgroundColor.color == null && backgroundColor.colorIndex == -1) {
            keywordHighlightView.backgroundColor = Color(colorPalette.getColor(TerminalColor.Basic.BACKGROUND))
        } else if (backgroundColor.color != null) {
            keywordHighlightView.backgroundColor = backgroundColor.color
        } else {
            keywordHighlightView.backgroundColor = Color(colorPalette.getXTerm256Color(backgroundColor.colorIndex))
        }
        keywordHighlightView.repaint()
    }

    override fun createCenterPanel(): JComponent {
        val layout = FormLayout(
            "left:pref, $formMargin, default:grow, 2dlu, left:pref",
            "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
        )

        val stylePanel = FormBuilder.create().debug(false)
            .layout(
                FormLayout(
                    "default:grow, $formMargin, default:grow, $formMargin, default:grow, $formMargin, default:grow",
                    "pref"
                )
            )
            .add(boldCheckBox).xy(1, 1)
            .add(italicCheckBox).xy(3, 1)
            .add(underlineCheckBox).xy(5, 1)
            .add(lineThroughCheckBox).xy(7, 1)
            .build()

        var rows = 1
        val step = 2
        return FormBuilder.create().layout(layout).debug(false)
            .padding("${if (SystemInfo.isWindows) formMargin else "0dlu"}, $formMargin, $formMargin, $formMargin")
            .add("${I18n.getString("termora.highlight.keyword")}:").xy(1, rows)
            .add(keywordTextField).xyw(3, rows, 3).apply { rows += step }
            .add("${I18n.getString("termora.highlight.description")}:").xy(1, rows)
            .add(descriptionTextField).xyw(3, rows, 3).apply { rows += step }
            .add("${I18n.getString("termora.highlight.text-color")}:").xy(1, rows)
            .add(textColor).xy(3, rows)
            .add(textColorRevert).xy(5, rows).apply { rows += step }
            .add("${I18n.getString("termora.highlight.background-color")}:").xy(1, rows)
            .add(backgroundColor).xy(3, rows)
            .add(backgroundColorRevert).xy(5, rows).apply { rows += step }
            .add(stylePanel).xyw(1, rows, 5, "fill, center").apply { rows += step }
            .add(keywordHighlightView).xyw(1, rows, 5).apply { rows += step }
            .build()
    }

    private fun createColorPanel(color: Color, title: String): ColorPanel {
        val owner = this
        val arc = UIManager.getInt("Component.arc")
        val lineBorder = FlatLineBorder(Insets(1, 1, 1, 1), DynamicColor.BorderColor, 1f, arc)
        val colorPanel = ColorPanel()
        colorPanel.background = color
        colorPanel.preferredSize = keywordTextField.preferredSize
        colorPanel.border = lineBorder
        colorPanel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    val dialog = ChooseColorTemplateDialog(owner, title)
                    dialog.setLocationRelativeTo(owner)
                    dialog.defaultColor = colorPanel.color ?: Color.orange
                    dialog.isVisible = true
                    if (dialog.ok.not()) return

                    colorPanel.colorIndex = -1
                    colorPanel.color = null
                    if (dialog.colorIndex in 1..16) {
                        colorPanel.colorIndex = dialog.colorIndex
                        colorPanel.background = Color(colorPalette.getXTerm256Color(dialog.colorIndex))
                    } else {
                        colorPanel.color = dialog.color
                    }
                    repaintKeywordHighlightView()
                }
            }
        })
        return colorPanel
    }

    override fun doOKAction() {
        if (keywordTextField.text.isBlank()) {
            keywordTextField.outline = "error"
            keywordTextField.requestFocusInWindow()
            return
        }


        val newTextColor = if (textColor.color != null) textColor.color?.toRGB() ?: 0
        else if (textColor.colorIndex == -1) 0
        else textColor.colorIndex


        val newBackgroundColor = if (backgroundColor.color != null) backgroundColor.color?.toRGB() ?: 0
        else if (backgroundColor.colorIndex == -1) 0
        else backgroundColor.colorIndex

        keywordHighlight = KeywordHighlight(
            keyword = keywordTextField.text,
            description = descriptionTextField.text,
            matchCase = matchCaseBtn.isSelected,
            regex = regexBtn.isSelected,
            textColor = newTextColor,
            backgroundColor = newBackgroundColor,
            bold = boldCheckBox.isSelected,
            italic = italicCheckBox.isSelected,
            lineThrough = lineThroughCheckBox.isSelected,
            underline = underlineCheckBox.isSelected,
        )

        super.doOKAction()
    }

    private fun Color.toRGB(): Int {
        return 65536 * red + 256 * green + blue
    }

}
