package app.termora.plugin.internal.plugin

import app.termora.*
import com.formdev.flatlaf.extras.components.FlatToolBar
import com.formdev.flatlaf.util.SystemInfo
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Window
import java.awt.event.ActionEvent
import java.net.URI
import javax.swing.*

internal class PluginRepositoryDialog(owner: Window) : DialogWrapper(owner) {

    private val model = DefaultListModel<String>()
    private val list = JList(model)
    private val dialog get() = this

    var changed = false
        private set

    init {
        size = Dimension(UIManager.getInt("Dialog.width") - 200, UIManager.getInt("Dialog.height") - 150)
        isModal = true
        isResizable = false
        title = I18n.getString("termora.settings.plugin.repository.title")
        list.fixedCellHeight = UIManager.getInt("Tree.rowHeight")
        for (url in PluginRepositoryManager.getInstance().getRepositories()) {
            model.addElement(url)
        }
        setLocationRelativeTo(owner)
        init()
    }

    override fun createCenterPanel(): JComponent {
        val pluginRepositoryManager = PluginRepositoryManager.getInstance()
        val panel = JPanel(BorderLayout())
        val toolbar = FlatToolBar().apply { isFloatable = false }
        val addBtn = JButton(Icons.add)
        val deleteBtn = JButton(Icons.delete)
        val urlBtn = JButton(Icons.externalLink)
        deleteBtn.isEnabled = false

        toolbar.add(addBtn)
        toolbar.add(deleteBtn)
        toolbar.add(Box.createHorizontalGlue())
        toolbar.add(urlBtn)
        toolbar.border = BorderFactory.createMatteBorder(0, 0, 1, 0, DynamicColor.BorderColor)

        panel.add(toolbar, BorderLayout.NORTH)
        panel.add(JScrollPane(list).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)
        panel.border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor)

        addBtn.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val text = OptionPane.showInputDialog(
                    dialog,
                    title = I18n.getString("termora.settings.plugin.repository.add")
                )
                if (text.isNullOrBlank()) return
                if ((text.startsWith("http://") || text.startsWith("https://")).not()) {
                    return
                }
                pluginRepositoryManager.addRepository(text)
                model.addElement(text)
                changed = true
            }
        })

        urlBtn.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (I18n.isChinaMainland()) {
                    Application.browse(URI.create("https://www.termora.cn/plugins/mirrors?version=${Application.getVersion()}"))
                } else {
                    Application.browse(URI.create("https://www.termora.app/plugins/mirrors?version=${Application.getVersion()}"))
                }
            }
        })

        deleteBtn.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (OptionPane.showConfirmDialog(
                        dialog,
                        I18n.getString("termora.keymgr.delete-warning"),
                        I18n.getString("termora.remove"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                    ) == JOptionPane.YES_OPTION
                ) {
                    for (i in list.selectedIndices.sortedByDescending { it }) {
                        pluginRepositoryManager.removeRepository(model.getElementAt(i))
                        model.removeElementAt(i)
                        changed = true
                    }
                }
            }
        })

        list.addListSelectionListener { deleteBtn.isEnabled = list.selectedIndex >= 0 }

        return panel
    }

    override fun addNotify() {
        super.addNotify()

        if (SystemInfo.isMacOS) {
            NativeMacLibrary.setControlsVisible(
                this,
                false,
                arrayOf(
                    NativeMacLibrary.NSWindowButton.NSWindowZoomButton,
                    NativeMacLibrary.NSWindowButton.NSWindowMiniaturizeButton
                )
            )
        }
    }

    override fun createSouthPanel(): JComponent? {
        return null
    }

}
