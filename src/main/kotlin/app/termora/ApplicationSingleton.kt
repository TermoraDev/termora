package app.termora

import com.formdev.flatlaf.util.SystemInfo
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.*
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinUser.*
import com.sun.jna.platform.win32.Wtsapi32
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean

class ApplicationSingleton private constructor() : Disposable {

    @Volatile
    private var isSingleton = null as Boolean?


    companion object {
        fun getInstance(): ApplicationSingleton {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(ApplicationSingleton::class) { ApplicationSingleton() }
        }
    }

    fun isSingleton(): Boolean {
        var singleton = this.isSingleton
        if (singleton != null) return singleton

        try {
            synchronized(this) {
                singleton = this.isSingleton
                if (singleton != null) return singleton

                if (SystemInfo.isWindows) {
                    val handle = Kernel32.INSTANCE.CreateMutex(null, false, Application.getName())
                    singleton = handle != null && Kernel32.INSTANCE.GetLastError() != WinError.ERROR_ALREADY_EXISTS
                    if (singleton) {
                        // 启动监听器，方便激活窗口
                        Thread.ofVirtual().start(Win32HelperWindow.getInstance())
                    } else {
                        // 尝试激活窗口
                        Win32HelperWindow.tick()
                    }
                } else {
                    singleton = FileLocker.getInstance().tryLock()
                }

                this.isSingleton = singleton == true
            }

        } catch (e: Exception) {
            e.printStackTrace(System.err)
            return false
        }


        return this.isSingleton == true

    }

    private class FileLocker private constructor() {
        companion object {
            fun getInstance(): FileLocker {
                return ApplicationScope.forApplicationScope()
                    .getOrCreate(FileLocker::class) { FileLocker() }
            }
        }


        private lateinit var singletonChannel: FileChannel
        private lateinit var singletonLock: FileLock


        fun tryLock(): Boolean {
            singletonChannel = FileChannel.open(
                Paths.get(Application.getBaseDataDir().absolutePath, "lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )

            val lock = singletonChannel.tryLock() ?: return false

            this.singletonLock = lock

            return true
        }
    }


    private class Win32HelperWindow private constructor() : Runnable {

        companion object {
            private val WindowClass = "${Application.getName()}HelperWindowClass"
            private val WindowName =
                "${Application.getName()} hidden helper window, used only to catch the windows events"
            private const val TICK: Int = WM_USER + 1

            fun getInstance(): Win32HelperWindow {
                return ApplicationScope.forApplicationScope()
                    .getOrCreate(Win32HelperWindow::class) { Win32HelperWindow() }
            }


            fun tick() {
                val hWnd = User32.INSTANCE.FindWindow(WindowClass, WindowName) ?: return
                User32.INSTANCE.SendMessage(hWnd, TICK, WPARAM(), LPARAM())
            }
        }

        private val isRunning = AtomicBoolean(false)

        override fun run() {
            if (SystemInfo.isWindows) {
                if (isRunning.compareAndSet(false, true)) {
                    Win32Window()
                }
            }
        }


        private class Win32Window : WindowProc {
            /**
             * Instantiates a new win32 window test.
             */
            init {
                // define new window class
                val hInst = Kernel32.INSTANCE.GetModuleHandle(null)

                val wClass = WNDCLASSEX()
                wClass.hInstance = hInst
                wClass.lpfnWndProc = this
                wClass.lpszClassName = WindowClass

                // register window class
                User32.INSTANCE.RegisterClassEx(wClass)

                // create new window
                val hWnd = User32.INSTANCE.CreateWindowEx(
                    User32.WS_EX_TOPMOST,
                    WindowClass,
                    WindowName,
                    0, 0, 0, 0, 0,
                    null,  // WM_DEVICECHANGE contradicts parent=WinUser.HWND_MESSAGE
                    null, hInst, null
                )


                val msg = MSG()
                while (User32.INSTANCE.GetMessage(msg, hWnd, 0, 0) > 0) {
                    User32.INSTANCE.TranslateMessage(msg)
                    User32.INSTANCE.DispatchMessage(msg)
                }

                Wtsapi32.INSTANCE.WTSUnRegisterSessionNotification(hWnd)
                User32.INSTANCE.UnregisterClass(WindowClass, hInst)
                User32.INSTANCE.DestroyWindow(hWnd)

            }

            override fun callback(hwnd: HWND, uMsg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
                when (uMsg) {
                    WM_CREATE -> {
                        return LRESULT()
                    }

                    TICK -> {
                        onTick()
                        return LRESULT()
                    }

                    WM_DESTROY -> {
                        User32.INSTANCE.PostQuitMessage(0)
                        return LRESULT()
                    }

                    else -> return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam)
                }
            }

            private fun onTick() {
                TermoraFrameManager.getInstance().tick()
            }

        }
    }
}