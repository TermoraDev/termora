package app.termora.plugin

interface Extension {
    /**
     * 越小越靠前
     */
    fun ordered(): Long = Long.MAX_VALUE

    /**
     * 如果在 [DispatchThread.EDT] 线程上派发则会做强制校验
     *
     * 如果返回 [DispatchThread.BGT] 也可以在 EDT 线程上派发，只是不会做校验
     *
     * 校验失败仅会打印日志提醒
     */
    fun getDispatchThread() = DispatchThread.EDT
}