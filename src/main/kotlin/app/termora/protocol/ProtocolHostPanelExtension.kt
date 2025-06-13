package app.termora.protocol

import app.termora.plugin.Extension
import app.termora.plugin.ExtensionManager

interface ProtocolHostPanelExtension : Extension {
    companion object {
        val extensions
            get() = ExtensionManager.getInstance()
                .getExtensions(ProtocolHostPanelExtension::class.java)
                .sortedBy { it.getProtocolProvider().ordered() }
    }

    /**
     * 获取协议提供者
     */
    fun getProtocolProvider(): ProtocolProvider

    /**
     * 创建协议主机面板
     */
    fun createProtocolHostPanel(): ProtocolHostPanel

}