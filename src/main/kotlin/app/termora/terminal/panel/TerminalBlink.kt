package app.termora.terminal.panel

import app.termora.ApplicationScope
import app.termora.Disposable
import app.termora.database.DatabaseManager
import app.termora.terminal.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

class TerminalBlink(terminal: Terminal) : Disposable {


    private var cursorBlinkJob: Job? = null
    private val terminalSettings get() = DatabaseManager.getInstance().terminal
    private val isDisposed = AtomicBoolean(false)
    private val globalBlink get() = GlobalBlink.getInstance()
    private val coroutineScope get() = globalBlink.coroutineScope

    /**
     * 返回 true 表示可以显示某些内容 [TextStyle.blink]
     */
    val blink get() = globalBlink.blink

    /**
     * 这个与 [blink] 不同的是它是控制光标的
     */
    @Volatile
    var cursorBlink = true
        private set

    init {

        reset()

        // 如果有写入，那么显示光标 N 秒
        terminal.getTerminalModel().addDataListener(object : DataListener {
            override fun onChanged(key: DataKey<*>, data: Any) {
                // 写入后，重置光标
                if (key == VisualTerminal.Written) {
                    reset()
                } else if (key == TerminalPanel.Focused) {
                    // 获取焦点的一瞬间则立即重置
                    if (data == true) {
                        reset()
                    }
                }
            }
        })
    }


    private fun reset() {
        if (isDisposed.get()) {
            return
        }

        cursorBlink = true
        cursorBlinkJob?.cancel()
        cursorBlinkJob = coroutineScope.launch {
            while (coroutineScope.isActive) {

                delay(500.milliseconds)

                if (isDisposed.get()) {
                    break
                }

                // 如果开启了光标闪烁才闪速
                cursorBlink = if (terminalSettings.cursorBlink) {
                    !cursorBlink
                } else {
                    true
                }

            }
        }
    }

    override fun dispose() {
        if (isDisposed.compareAndSet(false, true)) {
            cursorBlinkJob?.cancel()
        }
    }


    private class GlobalBlink : Disposable {

        companion object {
            fun getInstance(): GlobalBlink {
                return ApplicationScope.forApplicationScope()
                    .getOrCreate(GlobalBlink::class) { GlobalBlink() }
            }
        }

        val coroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

        /**
         * 返回 true 表示可以显示某些内容 [TextStyle.blink]
         */
        @Volatile
        var blink = true
            private set


        init {
            coroutineScope.launch {
                while (coroutineScope.isActive) {
                    delay(500)
                    blink = !blink
                }
            }
        }

        override fun dispose() {
            coroutineScope.cancel()
        }
    }
}