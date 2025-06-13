package app.termora.tree

import java.awt.Graphics2D
import javax.swing.JComponent

interface SimpleTreeCellAnnotation {
    companion object {
        /**
         * 间隙
         */
        const val SPACE = 6
    }

    /**
     * 标注宽度
     */
    fun getWidth(c: JComponent): Int

    /**
     * 渲染标注
     */
    fun paint(c: JComponent, g: Graphics2D)
}