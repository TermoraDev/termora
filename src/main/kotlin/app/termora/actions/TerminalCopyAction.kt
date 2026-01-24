package app.termora.actions

import app.termora.I18n
import org.slf4j.LoggerFactory
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException

class TerminalCopyAction : AnAction() {
    companion object {
        const val COPY = "TerminalCopy"
        private val log = LoggerFactory.getLogger(TerminalCopyAction::class.java)
    }

    init {
        putValue(SHORT_DESCRIPTION, I18n.getString("termora.actions.copy-from-terminal"))
        putValue(ACTION_COMMAND_KEY, COPY)
    }

    override fun actionPerformed(evt: AnActionEvent) {
        val terminalPanel = evt.getData(DataProviders.TerminalPanel) ?: return
        val selectionModel = terminalPanel.terminal.getSelectionModel()
        if (!selectionModel.hasSelection()) {
            return
        }
        val text = terminalPanel.copy()
        val systemClipboard = terminalPanel.toolkit.systemClipboard

        evt.consume()

        // 如果文本为空，那么清空剪切板
        if (text.isEmpty()) {
            systemClipboard.setContents(EmptyTransferable(), null)
            return
        }

        systemClipboard.setContents(StringSelection(text), null)
        if (log.isTraceEnabled) {
            log.trace("Copy to clipboard. {}", text)
        }
    }


    private class EmptyTransferable : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> {
            return emptyArray()
        }

        override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean {
            return false
        }

        override fun getTransferData(flavor: DataFlavor?): Any {
            throw UnsupportedFlavorException(flavor)
        }

    }

}
