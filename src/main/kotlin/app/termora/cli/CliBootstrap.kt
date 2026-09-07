package app.termora.cli

import app.termora.ApplicationInitializr
import app.termora.database.DatabaseManager

/** headless 初始化：不启动 GUI，仅准备数据库访问。 */
object CliBootstrap {
    @Volatile
    private var initialized = false

    @Synchronized
    fun init() {
        if (initialized) return
        // 绝不弹窗的硬保证：任何创建 Swing 窗口的代码会抛 HeadlessException
        System.setProperty("java.awt.headless", "true")
        // 打包版会把 sqlite 等 native 库抽到 app/dylib，需设置系统属性才能定位（与 GUI 启动一致）。
        // 开发版 appPath 为空，该调用内部直接 return，是 no-op。
        ApplicationInitializr.setupNativeLibraries()
        // 触发数据库初始化（其 init 会初始化 DatabaseSecret，自动解锁；数据库路径 baseDataDir/config/termora.db）
        DatabaseManager.getInstance()
        initialized = true
    }
}
