package app.termora

import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import app.termora.actions.MultipleAction
import app.termora.highlight.KeywordHighlightPaintListener
import app.termora.terminal.DataKey
import app.termora.terminal.PtyConnector
import app.termora.terminal.Terminal
import app.termora.terminal.panel.TerminalHyperlinkPaintListener
import app.termora.terminal.panel.TerminalPanel
import app.termora.terminal.panel.TerminalWriter
import kotlinx.coroutines.*
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.nio.charset.Charset
import java.util.*
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

class TerminalPanelFactory : Disposable {
    private val terminalPanels = mutableListOf<TerminalPanel>()

    companion object {

        fun getInstance(): TerminalPanelFactory {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(TerminalPanelFactory::class) { TerminalPanelFactory() }
        }
    }

    init {
        // repaint
        Painter.getInstance()
    }


    fun createTerminalPanel(tab: TerminalTab?, terminal: Terminal, ptyConnector: PtyConnector): TerminalPanel {
        val writer = MyTerminalWriter(ptyConnector)
        val terminalPanel = TerminalPanel(tab, terminal, writer)

        // processDeviceStatusReport
        terminal.getTerminalModel().setData(DataKey.TerminalWriter, writer)

        terminalPanel.addTerminalPaintListener(MultipleTerminalListener())
        terminalPanel.addTerminalPaintListener(KeywordHighlightPaintListener.getInstance())
        terminalPanel.addTerminalPaintListener(TerminalHyperlinkPaintListener.getInstance())

        Disposer.register(terminalPanel, object : Disposable {
            override fun dispose() {
                removeTerminalPanel(terminalPanel)
            }
        })

        addTerminalPanel(terminalPanel)
        return terminalPanel
    }

    fun getTerminalPanels(): Array<TerminalPanel> {
        return terminalPanels.toTypedArray()
    }

    fun repaintAll() {
        if (SwingUtilities.isEventDispatchThread()) {
            getTerminalPanels().forEach { it.repaintImmediate() }
        } else {
            SwingUtilities.invokeLater { repaintAll() }
        }
    }

    fun fireResize() {
        getTerminalPanels().forEach { c ->
            c.getListeners(ComponentListener::class.java).forEach {
                it.componentResized(ComponentEvent(c, ComponentEvent.COMPONENT_RESIZED))
            }
        }
    }

    private fun removeTerminalPanel(terminalPanel: TerminalPanel) {
        terminalPanels.remove(terminalPanel)
    }

    private fun addTerminalPanel(terminalPanel: TerminalPanel) {
        terminalPanels.add(terminalPanel)
    }

    private class Painter : Disposable {
        companion object {
            fun getInstance(): Painter {
                return ApplicationScope.forApplicationScope().getOrCreate(Painter::class) { Painter() }
            }
        }

        private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        init {
            coroutineScope.launch {
                while (coroutineScope.isActive) {
                    delay(500.milliseconds)
                    SwingUtilities.invokeLater { TerminalPanelFactory.getInstance().repaintAll() }
                }
            }
        }

        override fun dispose() {
            coroutineScope.cancel()
        }
    }

    private class MyTerminalWriter(private val ptyConnector: PtyConnector) : TerminalWriter {
        companion object {
            private val log = LoggerFactory.getLogger(MyTerminalWriter::class.java)
        }

        private lateinit var evt: AnActionEvent

        override fun onMounted(c: JComponent) {
            evt = AnActionEvent(c, StringUtils.EMPTY, EventObject(c))
        }

        override fun write(request: TerminalWriter.WriteRequest) {

            if (log.isDebugEnabled) {
                log.debug("write: ${String(request.buffer, getCharset())}")
            }

            val windowScope = evt.getData(DataProviders.WindowScope)
            if (windowScope == null) {
                ptyConnector.write(request.buffer)
                return
            }

            val multipleAction = MultipleAction.getInstance()
            if (multipleAction.isSelected(windowScope).not()) {
                ptyConnector.write(request.buffer)
                return
            }

            val terminalTabbedManager = evt.getData(DataProviders.TerminalTabbedManager)
            if (terminalTabbedManager == null) {
                ptyConnector.write(request.buffer)
                return
            }

            for (tab in terminalTabbedManager.getTerminalTabs()) {
                val writer = tab.getData(DataProviders.TerminalWriter) ?: continue
                if (writer is MyTerminalWriter) {
                    writer.ptyConnector.write(request.buffer)
                }
            }

        }

        override fun resize(rows: Int, cols: Int) {
            ptyConnector.resize(rows, cols)
        }

        override fun getCharset(): Charset {
            return ptyConnector.getCharset()
        }

    }

}