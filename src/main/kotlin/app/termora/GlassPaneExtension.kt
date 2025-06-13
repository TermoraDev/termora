package app.termora

import app.termora.plugin.Extension
import java.awt.Graphics2D
import javax.swing.JComponent

/**
 * 玻璃面板扩展
 */
interface GlassPaneExtension : Extension {

    /**
     * 渲染背景，如果返回 true 会立即退出。（当有多个扩展的时候，只会执行一个）
     *
     * @return true：渲染了背景，false：没有渲染背景
     */
    fun paint(c: JComponent, g2d: Graphics2D): Boolean

}