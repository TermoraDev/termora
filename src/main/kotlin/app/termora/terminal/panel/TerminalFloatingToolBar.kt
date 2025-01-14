package app.termora.terminal.panel

import app.termora.AnAction
import app.termora.HostTerminalTab
import app.termora.I18n
import app.termora.Icons
import app.termora.terminal.DataKey
import app.termora.terminal.DataListener
import app.termora.terminal.Terminal
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.ui.FlatRoundBorder
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.Box
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent

class TerminalFloatingToolBar(
    private val terminal: Terminal,
) : TerminalFloatingToolBarStateSupport {

    companion object {
        val StateSupport = DataKey(TerminalFloatingToolBarStateSupport::class)

        enum class State {
            Shown,
            Hidden,
            Removed
        }
    }

    private val actionComponent = Box.createHorizontalBox()
    private val actions = mutableListOf<AnAction>()
    private val pinButton = createPinButton()
    private val propertyChangeSupport = PropertyChangeSupport(this)

    val actionCount get() = actions.size
    var state = State.Hidden
        private set(value) {
            val oldValue = field
            field = value
            propertyChangeSupport.firePropertyChange("State", oldValue, value)
        }

    init {
        initView()
        initActions()
    }

    private fun initView() {
        actionComponent.isOpaque = false
        actionComponent.isFocusable = false
        actionComponent.border = FlatRoundBorder()
        actionComponent.isVisible = false

        // TerminalFloatingToolBarStateSupport
        terminal.getTerminalModel().setData(StateSupport, this)
    }

    private fun initActions() {
        // reconnect
        registerReconnectAction()
    }

    private fun registerReconnectAction() {
        if (terminal.getTerminalModel().hasData(HostTerminalTab.TerminalTab)) {
            val terminalTab = terminal.getTerminalModel().getData(HostTerminalTab.TerminalTab)
            addAction(object : AnAction(I18n.getString("termora.tabbed.contextmenu.reconnect"), Icons.refresh) {
                override fun actionPerformed(evt: ActionEvent) {
                    if (terminalTab.canReconnect()) {
                        terminalTab.reconnect()
                    }
                }
            })
        } else {
            terminal.getTerminalModel().addDataListener(object : DataListener {
                override fun onChanged(key: DataKey<*>, data: Any) {
                    if (key == HostTerminalTab.TerminalTab) {
                        terminal.getTerminalModel().removeDataListener(this)
                        registerReconnectAction()
                    }
                }
            })
        }
    }


    fun addAction(action: AnAction) {
        actions.add(action)
        repaint()
    }

    fun removeAction(action: AnAction) {
        actions.remove(action)
        repaint()
    }

    private fun repaint() {
        actionComponent.removeAll()

        if (actions.isNotEmpty()) {
            actionComponent.add(pinButton)

            for (i in 0 until this.actions.size) {
                val action = actions[i]
                val button = JButton(action.smallIcon)
                button.toolTipText = action.name
                button.addActionListener(action)
                button.putClientProperty(
                    FlatClientProperties.BUTTON_TYPE,
                    FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON
                )
                button.isFocusable = false
                actionComponent.add(button)
                actionComponent.add(Box.createHorizontalStrut(2))
            }


            actionComponent.add(createCloseButton())
        }

        actionComponent.revalidate()
        actionComponent.repaint()

    }


    private fun createPinButton(): JButton {
        return createButton(Icons.pin, Icons.pin, Icons.pin) {
            pinButton.isSelected = !pinButton.isSelected
        }
    }

    private fun createCloseButton(): JButton {
        return createButton(Icons.closeSmall, Icons.closeSmallHovered, Icons.closeSmallHovered) {
            pinButton.isSelected = false
            hide()
            state = State.Removed
        }
    }

    private fun createButton(
        icon: Icon,
        rolloverIcon: Icon,
        pressedIcon: Icon,
        actionListener: ActionListener
    ): JButton {
        val button = JButton(icon)
        button.rolloverIcon = rolloverIcon
        button.pressedIcon = pressedIcon
        button.addActionListener(actionListener)
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
        button.isFocusable = false
        return button
    }


    fun hide() {
        if (pinButton.isSelected) {
            return
        }

        actionComponent.isVisible = false
        state = State.Hidden
    }

    fun show() {
        if (state == State.Shown || state == State.Removed) {
            return
        }

        actionComponent.isVisible = true
        state = State.Shown
    }


    fun getJComponent(): JComponent {
        return actionComponent
    }

    override fun get(): State {
        return state
    }


    fun addPropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeSupport.addPropertyChangeListener(listener)
    }

    fun removePropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeSupport.removePropertyChangeListener(listener)
    }

}