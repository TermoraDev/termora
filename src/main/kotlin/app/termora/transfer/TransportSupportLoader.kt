package app.termora.transfer

import app.termora.Disposable

internal interface TransportSupportLoader : Disposable {

    /**
     * 获取传输支持
     */
    suspend fun getTransportSupport(): TransportSupport

    /**
     * 只有当 [isLoaded] 返回 true 时才能调用，为了不出现问题，只有 EDT 线程才能调用
     */
    fun getSyncTransportSupport(): TransportSupport

    /**
     * 是否已经加载，已经加载不表示可以正常使用，它仅证明已经加载可以同步调用
     */
    fun isLoaded(): Boolean

    /**
     * 快速检查是否已经成功打开
     */
    fun isOpened(): Boolean = true
}