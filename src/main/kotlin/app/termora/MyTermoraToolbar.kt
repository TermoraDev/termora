package app.termora

import app.termora.actions.StateAction
import app.termora.findeverywhere.FindEverywhereAction
import app.termora.plugin.internal.badge.Badge
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatToolBar
import java.awt.AWTEvent
import java.awt.Rectangle
import java.awt.event.AWTEventListener
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.*

internal class MyTermoraToolbar(private val windowScope: WindowScope) : FlatToolBar() {


    private val customizeToolBarAWTEventListener = CustomizeToolBarAWTEventListener()
    private val model get() = TermoraToolbarModel.getInstance()
    private val actionManager get() = model.getActionManager()
    private val toolbar get() = this

    /**
     * 一次性生命周期 每次刷新都会重置
     */
    private var disposable = Disposer.newDisposable()

    init {
        initView()
        initEvents()
        refreshActions()
    }

    private fun initView() {
        isFloatable = false
    }

    private fun initEvents() {
        Disposer.register(windowScope, object : Disposable {
            override fun dispose() {
                Disposer.dispose(disposable)
            }
        })


        // 监听全局事件
        toolkit.addAWTEventListener(customizeToolBarAWTEventListener, AWTEvent.MOUSE_EVENT_MASK)
        Disposer.register(windowScope, customizeToolBarAWTEventListener)

        // 监听变化
        model.addTermoraToolbarModelListener(object : TermoraToolbarModel.TermoraToolbarModelListener {
            override fun onChanged() {
                refreshActions()
            }
        }).let { Disposer.register(windowScope, it) }

    }

    private fun refreshActions() {
        Disposer.dispose(disposable)
        disposable = Disposer.newDisposable()

        removeAll()

        add(JButton(object : AbstractAction() {
            init {
                putValue(SMALL_ICON, Icons.add)
            }

            override fun actionPerformed(evt: ActionEvent) {
                actionManager.getAction(FindEverywhereAction.FIND_EVERYWHERE)?.actionPerformed(evt)
            }
        }))

        add(Box.createHorizontalGlue())

        for (action in model.getActions()) {
            if (action.visible.not()) continue
            val action = actionManager.getAction(action.id) ?: continue
            add(redirectAction(action, disposable))
        }

        revalidate()
        repaint()
    }

    private fun redirectAction(action: Action, disposable: Disposable): AbstractButton {
        val button = if (action is StateAction) JToggleButton() else JButton()
        button.toolTipText = action.getValue(Action.SHORT_DESCRIPTION) as? String
        button.icon = action.getValue(Action.SMALL_ICON) as? Icon
        button.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                action.actionPerformed(e)
                if (action is StateAction) {
                    button.isSelected = action.isSelected(windowScope)
                }
            }
        })

        val listener = object : PropertyChangeListener, Disposable {
            private val badge get() = Badge.getInstance(windowScope)
            override fun propertyChange(evt: PropertyChangeEvent) {
                if (evt.propertyName == "Badge") {
                    if (action.getValue("Badge") == true) {
                        badge.addBadge(button)
                    } else {
                        badge.removeBadge(button)
                    }
                }

                if (action is StateAction) {
                    button.isSelected = action.isSelected(windowScope)
                }
            }

            override fun dispose() {
                action.removePropertyChangeListener(this)
            }
        }

        action.addPropertyChangeListener(listener)
        Disposer.register(disposable, listener)

        return button
    }

    /**
     * 对着 ToolBar 右键
     */
    private inner class CustomizeToolBarAWTEventListener : AWTEventListener, Disposable {
        override fun eventDispatched(event: AWTEvent) {
            if (event !is MouseEvent || event.id != MouseEvent.MOUSE_CLICKED
                || SwingUtilities.isRightMouseButton(event).not()
            ) return

            // 如果 ToolBar 没有显示
            if (toolbar.isShowing.not()) return

            // 如果不是作用于在 ToolBar 上面
            if (Rectangle(toolbar.locationOnScreen, toolbar.size).contains(event.locationOnScreen).not()) return

            // 显示右键菜单
            showContextMenu(event)
        }

        private fun showContextMenu(event: MouseEvent) {
            val popupMenu = FlatPopupMenu()
            popupMenu.add(I18n.getString("termora.toolbar.customize-toolbar")).addActionListener {
                val owner = windowScope.window
                val dialog = CustomizeToolBarDialog(owner, windowScope, model)
                dialog.setLocationRelativeTo(owner)
                if (dialog.open()) {
                    model.setActions(dialog.getActions())
                }
            }
            popupMenu.show(event.component, event.x, event.y)
        }

        override fun dispose() {
            toolkit.removeAWTEventListener(this)
        }
    }
}