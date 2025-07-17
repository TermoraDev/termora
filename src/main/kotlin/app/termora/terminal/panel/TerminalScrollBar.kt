package app.termora.terminal.panel

import app.termora.swingCoroutineScope
import app.termora.terminal.Terminal
import app.termora.terminal.TerminalColor
import com.formdev.flatlaf.ui.FlatScrollBarUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JScrollBar
import javax.swing.SwingUtilities
import javax.swing.plaf.ScrollBarUI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

class TerminalScrollBar(
    private val terminalPanel: TerminalPanel,
    private val terminalFindPanel: TerminalFindPanel,
    private val terminal: Terminal
) : JScrollBar() {
    private val colorPalette get() = terminal.getTerminalModel().getColorPalette()
    private val myUI = MyScrollBarUI()
    private val owner get() = SwingUtilities.getWindowAncestor(this)


    init {
        setUI(myUI)
        initEvents()
    }

    override fun setUI(ui: ScrollBarUI) {
        super.setUI(myUI)
    }

    private fun initEvents() {
        val previewMouseAdapter = PreviewMouseAdapter()
        addMouseMotionListener(previewMouseAdapter)
        addMouseListener(previewMouseAdapter)
    }

    private fun drawFindMap(g: Graphics, trackBounds: Rectangle) {
        if (!terminalPanel.findMap) return
        val kinds = terminalFindPanel.kinds
        if (kinds.isEmpty()) return

        val averageCharWidth = terminalPanel.getAverageCharWidth() * 2
        val count = max(terminal.getDocument().getLineCount(), terminal.getTerminalModel().getRows())
        // 高度/总行数 就可以计算出标记的平均高度
        val lineHeight = max(ceil(1.0 * trackBounds.height / count).toInt(), 1)

        val rows = linkedSetOf<Int>()
        for (kind in kinds) {
            rows.add(kind.startPosition.y)
            rows.add(kind.endPosition.y)
        }

        g.color = Color(colorPalette.getColor(TerminalColor.Find.BACKGROUND))

        for (row in rows) {
            // 计算行应该在总行的哪个位置
            val n = row * 1.0 / count
            // 根据比例计算出标记的位置
            val y = max(ceil(trackBounds.height * n).toInt() - lineHeight, 0)
            g.fillRect(trackBounds.width - averageCharWidth, y, averageCharWidth, lineHeight)
        }
    }


    private inner class MyScrollBarUI : FlatScrollBarUI() {
        override fun paintTrack(g: Graphics, c: JComponent, trackBounds: Rectangle) {
            super.paintTrack(g, c, trackBounds)
            drawFindMap(g, trackBounds)
        }

        public override fun getThumbBounds(): Rectangle {
            return super.getThumbBounds()
        }
    }

    private inner class PreviewMouseAdapter : MouseAdapter() {
        private var job: Job? = null

        override fun mouseMoved(e: MouseEvent) {
            if (terminal.getScrollingModel().canVerticalScroll().not()) {
                mouseExited(e)
                return
            }

            if (myUI.thumbBounds.contains(e.point)) {
                mouseExited(e)
                return
            }

            if (terminalPanel.isShowingPreview()) {
                doMouseMoved(e)
            } else {
                job?.cancel()
                job = swingCoroutineScope.launch(Dispatchers.Swing) {
                    delay(250.milliseconds)
                    doMouseMoved(e)
                }
            }
        }

        private fun doMouseMoved(e: MouseEvent) {
            if (owner.isFocused.not()) return
            terminalPanel.showPreview(e.locationOnScreen)
        }

        override fun mouseExited(e: MouseEvent) {
            job?.cancel()
            terminalPanel.hidePreview()
        }

    }
}