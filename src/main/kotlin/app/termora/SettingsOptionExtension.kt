package app.termora

import app.termora.plugin.Extension

/**
 * 设置选项扩展
 */
interface SettingsOptionExtension : Extension {
    /**
     * 创建选项
     */
    fun createSettingsOption(): OptionsPane.Option
}