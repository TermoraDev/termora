package app.termora.tlog

import app.termora.*
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.database.DatabaseManager
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.util.SystemInfo
import org.apache.commons.io.FileUtils
import java.awt.Window
import java.io.File
import java.time.LocalDate
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

class TerminalLoggerAction : AnAction(I18n.getString("termora.terminal-logger"), Icons.listFiles) {
    private val properties get() = DatabaseManager.getInstance().properties

    /**
     * 是否开启了记录
     */
    var isRecording = properties.getString("terminal.logger.isRecording")?.toBoolean() ?: false
        private set(value) {
            field = value
            // firePropertyChange
            putValue("Recording", value)
            properties.putString("terminal.logger.isRecording", value.toString())
        }

    init {
        smallIcon = if (isRecording) Icons.dotListFiles else Icons.listFiles
    }

    override fun actionPerformed(evt: AnActionEvent) {
        val source = evt.source
        if (source !is JComponent) return

        val popupMenu = FlatPopupMenu()
        if (isRecording) {
            // stop
            popupMenu.add(I18n.getString("termora.terminal-logger.stop-recording")).addActionListener {
                isRecording = false
                smallIcon = Icons.listFiles
            }
        } else {
            // start
            popupMenu.add(I18n.getString("termora.terminal-logger.start-recording")).addActionListener {
                isRecording = true
                smallIcon = Icons.dotListFiles
            }
        }

        popupMenu.addSeparator()

        // 打开日志浏览
        popupMenu.add(I18n.getString("termora.terminal-logger.open-log-viewer")).addActionListener {
            openLogViewer(SwingUtilities.getWindowAncestor(source))
        }

        // 打开日志文件夹
        popupMenu.add(
            I18n.getString(
                "termora.terminal-logger.open-in-folder",
                if (SystemInfo.isMacOS) I18n.getString("termora.finder")
                else if (SystemInfo.isWindows) I18n.getString("termora.explorer")
                else I18n.getString("termora.folder")
            )
        ).addActionListener {
            val dir = getLogDir()
            Application.browse(dir.toURI())
        }

        val width = popupMenu.preferredSize.width
        popupMenu.show(source, -(width / 2) + source.width / 2, source.height)
    }

    private fun openLogViewer(owner: Window) {
        val fc = FileChooser()
        fc.allowsMultiSelection = true
        fc.title = I18n.getString("termora.terminal-logger.open-log-viewer")
        fc.fileSelectionMode = JFileChooser.FILES_ONLY

        if (SystemInfo.isMacOS) {
            fc.osxAllowedFileTypes = listOf("log")
        } else if (SystemInfo.isWindows) {
            fc.win32Filters.add(Pair("Log files", listOf("log")))
        }

        fc.defaultDirectory = getLogDir().absolutePath
        fc.showOpenDialog(owner).thenAccept { files ->
            if (files.isNotEmpty()) {
                SwingUtilities.invokeLater {
                    val manager = ApplicationScope.forWindowScope(owner).get(TerminalTabbedManager::class)
                    for (file in files) {
                        val tab = LogViewerTerminalTab(ApplicationScope.forWindowScope(owner), file)
                        tab.start()
                        manager.addTerminalTab(tab)
                    }
                }
            }
        }
    }

    fun getLogDir(): File {
        val dir = FileUtils.getFile(Application.getBaseDataDir(), "terminal", "logs", LocalDate.now().toString())
        FileUtils.forceMkdir(dir)
        return dir
    }
}