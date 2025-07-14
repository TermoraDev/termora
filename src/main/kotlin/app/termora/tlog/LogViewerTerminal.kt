package app.termora.tlog

import app.termora.TerminalFactory
import app.termora.terminal.*
import app.termora.terminal.panel.TerminalPanel
import org.slf4j.LoggerFactory

class LogViewerTerminal : TerminalFactory.MyVisualTerminal() {
    companion object {
        private val log = LoggerFactory.getLogger(LogViewerTerminal::class.java)
    }

    private val document by lazy { MyDocument(this) }
    private val terminalModel by lazy { LogViewerTerminalModel(this) }

    override fun getDocument(): Document {
        return document
    }

    override fun getTerminalModel(): TerminalModel {
        return terminalModel
    }

    private class MyDocument(terminal: Terminal) : DocumentImpl(terminal) {
        override fun eraseInDisplay(n: Int) {
            // 预览日志的时候，不处理清屏操作，不然会导致日志看不到。
            // 例如，用户输入了 cat xxx.txt ，然后执行了 clear 那么就看不到了
            if (n == 3) {
                if (log.isDebugEnabled) {
                    log.debug("ignore $n eraseInDisplay")
                }
                return
            }
            super.eraseInDisplay(n)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private class LogViewerTerminalModel(terminal: Terminal) : TerminalFactory.MyTerminalModel(terminal) {
        override fun getMaxRows(): Int {
            return Int.MAX_VALUE
        }

        override fun <T : Any> getData(key: DataKey<T>): T {
            if (key == DataKey.ShowCursor) {
                return false as T
            }
            return super.getData(key)
        }

        override fun <T : Any> getData(key: DataKey<T>, defaultValue: T): T {
            if (key == DataKey.ShowCursor) {
                return false as T
            } else if (key == TerminalPanel.FocusMode) {
                return false as T
            }
            return super.getData(key, defaultValue)
        }
    }
}