package app.termora.protocol

import app.termora.Disposable
import app.termora.Host
import java.awt.BorderLayout
import java.awt.Component
import java.awt.KeyboardFocusManager
import javax.swing.JPanel
import javax.swing.SwingUtilities

abstract class ProtocolHostPanel : JPanel(BorderLayout()), Disposable {

    private var lastFocusOwner: Component? = null

    /**
     * 获取 Host
     */
    abstract fun getHost(): Host

    /**
     * 设置 Host
     */
    abstract fun setHost(host: Host)

    /**
     * 验证字段
     */
    abstract fun validateFields(): Boolean

    /**
     * 隐藏之前
     */
    open fun onBeforeHidden() {
        val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return
        if (SwingUtilities.isDescendingFrom(owner, this)) {
            lastFocusOwner = owner
        }
    }

    /**
     * 隐藏之后
     */
    open fun onHidden() {}

    /**
     * 显示之前
     */
    open fun onBeforeShown() {}

    /**
     * 显示之后
     */
    open fun onShown() {
        val owner = lastFocusOwner
        if (owner == null) {
            requestFocusInWindow()
        } else {
            owner.requestFocusInWindow()
        }
    }
}