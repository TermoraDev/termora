package app.termora

import java.beans.PropertyChangeListener
import javax.swing.Icon
import javax.swing.JComponent

interface TerminalTab : Disposable {

    /**
     * 标题
     */
    fun getTitle(): String

    /**
     * 图标
     */
    fun getIcon(): Icon

    fun addPropertyChangeListener(listener: PropertyChangeListener)
    fun removePropertyChangeListener(listener: PropertyChangeListener)

    /**
     * 显示组件
     */
    fun getJComponent(): JComponent

    /**
     * 重连
     */
    fun reconnect() {}

    /**
     * 是否可以重连
     */
    fun canReconnect(): Boolean = true

    fun onLostFocus() {}
    fun onGrabFocus() {}

    /**
     * @return 返回 false 则不可关闭
     */
    fun canClose(): Boolean = true


}