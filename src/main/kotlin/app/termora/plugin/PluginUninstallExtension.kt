package app.termora.plugin

interface PluginUninstallExtension : Extension {
    /**
     * 插件开发者不需要做任何处理，但是仍然可以清理存储的配置文件。但是在本次程序退出前，应该正常保持工作
     */
    fun uninstalled()
}