package app.termora.terminal

import org.apache.commons.lang3.ArrayUtils
import org.slf4j.LoggerFactory
import java.awt.Toolkit
import kotlin.reflect.cast

open class TerminalModelImpl(private val terminal: Terminal) : TerminalModel {
    private var rows: Int = 27
    private var cols: Int = 80
    private val data = mutableMapOf<DataKey<*>, Any>()
    private var listeners = emptyArray<DataListener>()
    private val colorPalette = ColorPaletteImpl(terminal)

    companion object {
        private val log = LoggerFactory.getLogger(TerminalModelImpl::class.java)
    }


    init {

        // 默认样式
        this.setData(DataKey.TextStyle, TextStyle.Default)
        // 光标样式
        this.setData(DataKey.CursorStyle, CursorStyle.Block)
        // 显示光标
        this.setData(DataKey.ShowCursor, true)
        // 默认字符集
        this.setData(DataKey.GraphicCharacterSet, GraphicCharacterSet())
        // 滚动区域
        if (!this.hasData(DataKey.ScrollingRegion)) {
            this.setData(DataKey.ScrollingRegion, ScrollingRegion(1, this.getRows()))
        }
        // 鼠标上报策略
        this.setData(DataKey.MouseMode, MouseMode.MOUSE_REPORTING_NONE)

        this.addDataListener(object : DataListener {
            override fun onChanged(key: DataKey<*>, data: Any) {
                // 更多信息请看 DataKey.ScrollingRegion
                if (key == TerminalModel.Resize) {
                    val resize = TerminalModel.Resize.clazz.cast(data)
                    var region: ScrollingRegion
                    if (hasData(DataKey.ScrollingRegion)) {
                        region = getData(DataKey.ScrollingRegion)
                        region = region.copy(bottom = resize.newSize.rows)
                    } else {
                        region = ScrollingRegion(top = 1, bottom = resize.newSize.rows)
                    }
                    setData(DataKey.ScrollingRegion, region)
                    if (log.isDebugEnabled) {
                        log.debug("Resize ScrollingRegion. {}", region)
                    }
                }
            }
        })
    }

    override fun getTerminal(): Terminal {
        return terminal
    }

    override fun getCols(): Int {
        return cols
    }

    override fun getRows(): Int {
        return rows
    }


    override fun getColorPalette(): ColorPalette {
        return colorPalette
    }

    override fun <T : Any> getData(key: DataKey<T>): T {
        return key.clazz.cast(data[key])
    }

    override fun <T : Any> getData(key: DataKey<T>, defaultValue: T): T {
        if (data.containsKey(key)) {
            return getData(key)
        }
        return defaultValue
    }

    override fun <T : Any> setData(key: DataKey<T>, data: T) {
        this.data[key] = data
        fireDataChanged(key, data)
    }

    override fun hasData(key: DataKey<*>): Boolean {
        return data.containsKey(key)
    }

    override fun addDataListener(listener: DataListener) {
        listeners = ArrayUtils.add(listeners, listener)
    }

    override fun removeDataListener(listener: DataListener) {
        listeners = ArrayUtils.removeElement(listeners, listener)
    }

    override fun bell() {
        Toolkit.getDefaultToolkit().beep()
    }

    override fun resize(rows: Int, cols: Int) {

        if (rows < 5 || cols < 10) {
            return
        }

        val oldRows = getRows()
        val oldCols = getCols()


        this.rows = rows
        this.cols = cols


        fireDataChanged(
            TerminalModel.Resize, TerminalResize(
                TerminalSize(rows = oldRows, cols = oldCols),
                TerminalSize(rows = rows, cols = cols)
            )
        )

    }


    @Suppress("MemberVisibilityCanBePrivate")
    protected fun <T : Any> fireDataChanged(key: DataKey<T>, data: T) {
        for (listener in listeners) {
            listener.onChanged(key, data)
        }
    }


    override fun getMaxRows(): Int {
        return 5000
    }

}
