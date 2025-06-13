package app.termora.protocol

import app.termora.DynamicIcon
import app.termora.Icons
import app.termora.plugin.ExtensionManager
import org.apache.commons.lang3.StringUtils

/**
 * 协议
 */
interface ProtocolProvider {

    companion object {
        val providers
            get() = ExtensionManager.getInstance().getExtensions(ProtocolProviderExtension::class.java)
                .map { it.getProtocolProvider() }

        fun valueOf(protocol: String): ProtocolProvider? {
            return providers.firstOrNull { StringUtils.equalsIgnoreCase(it.getProtocol(), protocol) }
        }
    }

    /**
     * 如果返回 true 则表示这个协议仅在运行时生效
     */
    fun isTransient(): Boolean = false

    /**
     * 是否是传输协议，如果返回 true 那么就是对 SFTP 的扩展
     */
    fun isTransfer(): Boolean = false

    /**
     * 协议图标
     */
    fun getIcon(width: Int = 16, height: Int = 16): DynamicIcon = Icons.terminal

    /**
     * 协议
     */
    fun getProtocol(): String

    /**
     * 越小越靠前
     */
    fun ordered(): Int = Int.MAX_VALUE
}