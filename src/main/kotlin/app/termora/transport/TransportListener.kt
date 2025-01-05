package app.termora.transport

import java.util.*

interface TransportListener : EventListener {
    /**
     * Added
     */
    fun onTransportAdded(transport: Transport)

    /**
     * Removed
     */
    fun onTransportRemoved(transport: Transport)

    /**
     * 状态变化
     */
    fun onTransportChanged(transport: Transport)
}