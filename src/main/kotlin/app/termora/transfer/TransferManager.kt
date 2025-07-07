package app.termora.transfer

import app.termora.Disposable

interface TransferManager {


    /**
     * 添加传输任务
     */
    fun addTransfer(transfer: Transfer): Boolean

    /**
     * 移除传输任务
     */
    fun removeTransfer(id: String)

    /**
     * 获取任务
     */
    fun getTransfers(): Collection<Transfer>

    /**
     * 任务数量
     */
    fun getTransferCount(): Int

    /**
     * 传输监听器
     */
    fun addTransferListener(listener: TransferListener): Disposable

    /**
     * 移除传输监听器
     */
    fun removeTransferListener(listener: TransferListener)
}