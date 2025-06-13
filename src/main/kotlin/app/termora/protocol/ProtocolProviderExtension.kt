package app.termora.protocol

import app.termora.plugin.DispatchThread
import app.termora.plugin.Extension

/**
 * 协议提供者扩展
 */
interface ProtocolProviderExtension : Extension {
    /**
     * 协议提供者
     */
    fun getProtocolProvider(): ProtocolProvider

    override fun getDispatchThread(): DispatchThread {
        return DispatchThread.BGT
    }
}