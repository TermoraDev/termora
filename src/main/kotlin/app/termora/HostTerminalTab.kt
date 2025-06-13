package app.termora

import app.termora.actions.DataProvider
import app.termora.actions.DataProviders
import app.termora.terminal.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.swing.Swing
import java.beans.PropertyChangeEvent
import javax.swing.Icon

abstract class HostTerminalTab(
    val windowScope: WindowScope,
    val host: Host,
    protected val terminal: Terminal = TerminalFactory.getInstance().createTerminal()
) : PropertyTerminalTab(), DataProvider {
    companion object {
        val Host = DataKey(app.termora.Host::class)
    }

    protected val coroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Swing) }
    protected val terminalModel get() = terminal.getTerminalModel()
    protected var unread = false
        set(value) {
            field = value
            firePropertyChange(PropertyChangeEvent(this, "icon", null, null))
        }


    /*    visualTerminal    */
    protected fun Terminal.clearScreen() {
        this.write("${ControlCharacters.ESC}[3J")
    }

    init {
        terminal.getTerminalModel().setData(Host, host)
        terminal.getTerminalModel().addDataListener(object : DataListener {
            override fun onChanged(key: DataKey<*>, data: Any) {
                if (key == VisualTerminal.Written) {
                    if (hasFocus || unread) {
                        return
                    }
                    unread = true
                }
            }
        })
    }

    open fun start() {}

    override fun getTitle(): String {
        return host.name
    }

    override fun getIcon(): Icon {
        return Icons.terminal
    }

    override fun dispose() {
        terminal.close()
        coroutineScope.cancel()
    }

    override fun onGrabFocus() {
        super.onGrabFocus()
        if (!unread) return
        unread = false
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        if (dataKey == DataProviders.Terminal) {
            return terminal as T?
        }
        return null
    }
}