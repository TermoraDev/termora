package app.termora

import app.termora.terminal.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.swing.Swing
import java.beans.PropertyChangeEvent
import javax.swing.Icon

abstract class HostTerminalTab(
    val host: Host,
    protected val terminal: Terminal = TerminalFactory.instance.createTerminal()
) : PropertyTerminalTab() {
    companion object {
        val Host = DataKey(app.termora.Host::class)
    }

    protected val coroutineScope by lazy { CoroutineScope(Dispatchers.Swing) }
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
        if (host.protocol == Protocol.Local || host.protocol == Protocol.SSH) {
            return if (unread) Icons.terminalUnread else Icons.terminal
        }
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

}