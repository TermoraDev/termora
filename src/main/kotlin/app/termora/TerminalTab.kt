package app.termora

import app.termora.actions.DataProvider
import java.beans.PropertyChangeListener
import javax.swing.Icon
import javax.swing.JComponent

interface TerminalTab : Disposable, DataProvider {

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

    /**
     * 返回 true 表示可以关闭，只有当 [app.termora.database.DatabaseManager.Appearance.confirmTabClose] 为 false 时才会调用
     */
    fun willBeClose(): Boolean = true

    /**
     * 即将关闭，已经无法挽回
     */
    fun beforeClose() {}

    /**
     * 是否可以克隆
     */
    fun canClone(): Boolean = true


}