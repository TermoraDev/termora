package app.termora

import app.termora.database.DatabaseManager
import app.termora.nv.osx.NativeMacLibrary
import app.termora.plugin.ExtensionManager
import com.formdev.flatlaf.ui.FlatNativeWindowsLibrary
import com.formdev.flatlaf.util.SystemInfo
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser.*
import de.jangassen.jfa.ThreadUtils
import de.jangassen.jfa.foundation.Foundation
import de.jangassen.jfa.foundation.ID
import org.slf4j.LoggerFactory
import java.awt.Frame
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE
import kotlin.math.max
import kotlin.system.exitProcess


class TermoraFrameManager : Disposable {

    companion object {
        private val log = LoggerFactory.getLogger(TermoraFrameManager::class.java)

        fun getInstance(): TermoraFrameManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(TermoraFrameManager::class) { TermoraFrameManager() }
        }
    }

    private val frames = mutableListOf<TermoraFrame>()
    private val properties get() = DatabaseManager.getInstance().properties
    private val isDisposed = AtomicBoolean(false)
    private val isBackgroundRunning get() = DatabaseManager.getInstance().appearance.backgroundRunning
    private val frameExtensions get() = ExtensionManager.getInstance().getExtensions(FrameExtension::class.java)

    fun createWindow(): TermoraFrame {
        val frame = TermoraFrame().apply { registerCloseCallback(this) }
        frame.title = if (SystemInfo.isLinux) null else Application.getName()
        frame.defaultCloseOperation = DO_NOTHING_ON_CLOSE

        val rectangle = getFrameRectangle() ?: FrameRectangle(-1, -1, 1280, 800, 0)
        if (rectangle.isMaximized) {
            frame.setSize(1280, 800)
            frame.setLocationRelativeTo(null)
            frame.extendedState = rectangle.s
        } else {
            // 控制最小
            frame.setSize(
                max(rectangle.w, UIManager.getInt("Dialog.width") - 150),
                max(rectangle.h, UIManager.getInt("Dialog.height") - 100)
            )
            if (rectangle.x == -1 && rectangle.y == -1) {
                frame.setLocationRelativeTo(null)
            } else {
                frame.setLocation(max(rectangle.x, 0), max(rectangle.y, 0))
            }
        }

        frame.addNotifyListener(object : NotifyListener {
            private val opacity get() = DatabaseManager.getInstance().appearance.opacity
            override fun addNotify() {
                val opacity = this.opacity
                if (opacity >= 1.0) return
                setOpacity(frame, opacity)
            }
        })

        for (extension in frameExtensions) {
            extension.customize(frame)
        }

        return frame.apply { frames.add(this) }
    }

    fun getWindows(): Array<TermoraFrame> {
        return frames.toTypedArray()
    }


    private fun registerCloseCallback(window: TermoraFrame) {
        val manager = this
        window.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {

                // 存储位置信息
                saveFrameRectangle(window)

                // 删除
                frames.remove(window)

                // dispose windowScope
                val windowScope = ApplicationScope.forWindowScope(e.window)
                Disposer.disposeChildren(windowScope, null)
                Disposer.dispose(windowScope)

                val windowScopes = ApplicationScope.windowScopes()
                if (windowScopes.isNotEmpty()) {
                    return
                }

                // 如果已经没有 Window 域了，那么就可以退出程序了
                if (SystemInfo.isWindows || SystemInfo.isLinux) {
                    Disposer.dispose(manager)
                } else if (SystemInfo.isMacOS) {
                    // 如果 macOS 开启了后台运行，那么尽管所有窗口都没了，也不会退出
                    if (isBackgroundRunning) {
                        return
                    }
                    Disposer.dispose(manager)
                }
            }

            override fun windowClosing(e: WindowEvent) {
                if (ApplicationScope.windowScopes().size != 1) {
                    window.dispose()
                    return
                }

                // 如果 Windows 开启了后台运行，那么最小化
                if (SystemInfo.isWindows && isBackgroundRunning) {
                    // 最小化
                    window.extendedState = window.extendedState or JFrame.ICONIFIED
                    // 隐藏
                    window.isVisible = false
                    return
                }

                // 如果 macOS 已经开启了后台运行，那么直接销毁，因为会有一个进程驻守
                if (SystemInfo.isMacOS && isBackgroundRunning) {
                    window.dispose()
                    return
                }

                val option = OptionPane.showConfirmDialog(
                    window,
                    I18n.getString("termora.quit-confirm", Application.getName()),
                    optionType = JOptionPane.YES_NO_OPTION,
                )
                if (option == JOptionPane.YES_OPTION) {
                    window.dispose()
                }
            }
        })
    }

    fun tick() {
        if (SwingUtilities.isEventDispatchThread()) {
            val windows = getWindows()
            if (windows.isEmpty()) return
            for (window in windows) {
                if (window.extendedState and JFrame.ICONIFIED == JFrame.ICONIFIED) {
                    window.extendedState = window.extendedState and JFrame.ICONIFIED.inv()
                }
                window.isVisible = true
            }
            windows.last().toFront()
        } else {
            SwingUtilities.invokeLater { tick() }
        }
    }

    override fun dispose() {
        if (isDisposed.compareAndSet(false, true)) {
            Disposer.dispose(ApplicationScope.forApplicationScope())

            try {
                Disposer.getTree().assertIsEmpty(true)
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            }
        }

        exitProcess(0)
    }

    private fun saveFrameRectangle(frame: TermoraFrame) {
        properties.putString("TermoraFrame.x", frame.x.toString())
        properties.putString("TermoraFrame.y", frame.y.toString())
        properties.putString("TermoraFrame.width", frame.width.toString())
        properties.putString("TermoraFrame.height", frame.height.toString())
        properties.putString("TermoraFrame.extendedState", frame.extendedState.toString())
    }

    private fun getFrameRectangle(): FrameRectangle? {
        val x = properties.getString("TermoraFrame.x")?.toIntOrNull() ?: return null
        val y = properties.getString("TermoraFrame.y")?.toIntOrNull() ?: return null
        val w = properties.getString("TermoraFrame.width")?.toIntOrNull() ?: return null
        val h = properties.getString("TermoraFrame.height")?.toIntOrNull() ?: return null
        val s = properties.getString("TermoraFrame.extendedState")?.toIntOrNull() ?: return null
        return FrameRectangle(x, y, w, h, s)
    }

    fun setOpacity(opacity: Double) {
        if (opacity < 0 || opacity > 1 || SystemInfo.isLinux) return
        for (window in getWindows()) {
            setOpacity(window, opacity)
        }
    }

    private fun setOpacity(window: Window, opacity: Double) {
        if (SystemInfo.isMacOS) {
            val nsWindow = ID(NativeMacLibrary.getNSWindow(window) ?: return)
            ThreadUtils.dispatch_async {
                Foundation.invoke(nsWindow, "setOpaque:", false)
                Foundation.invoke(nsWindow, "setAlphaValue:", opacity)
            }
        } else if (SystemInfo.isWindows) {
            val alpha = ((opacity * 255).toInt() and 0xFF).toByte()
            val hwnd = WinDef.HWND(Pointer.createConstant(FlatNativeWindowsLibrary.getHWND(window)))
            val exStyle = User32.INSTANCE.GetWindowLong(hwnd, GWL_EXSTYLE)
            if (exStyle and WS_EX_LAYERED == 0) {
                User32.INSTANCE.SetWindowLong(hwnd, GWL_EXSTYLE, exStyle or WS_EX_LAYERED)
            }
            User32.INSTANCE.SetLayeredWindowAttributes(hwnd, 0, alpha, LWA_ALPHA)
        }
    }

    private data class FrameRectangle(
        val x: Int, val y: Int, val w: Int, val h: Int, val s: Int
    ) {
        val isMaximized get() = (s and Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH
    }
}