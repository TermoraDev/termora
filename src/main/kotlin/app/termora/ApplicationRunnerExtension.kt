package app.termora

import app.termora.plugin.DispatchThread
import app.termora.plugin.Extension

interface ApplicationRunnerExtension : Extension {
    /**
     * 准备就绪，说明数据库、插件、i18n 等一切数据准备就绪，下一步就是启动窗口。
     *
     * 插件可以在这里初始化自己的数据
     */
    fun ready()

    override fun getDispatchThread() = DispatchThread.BGT
}