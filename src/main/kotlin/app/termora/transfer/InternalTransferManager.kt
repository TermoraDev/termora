package app.termora.transfer

import app.termora.Disposable
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

internal interface InternalTransferManager {
    enum class TransferMode {
        Delete,
        Transfer,
        ChangePermission,
        Rmrf,
    }

    /**
     * 是否允许传输，添加任务之前请调用
     */
    fun canTransfer(paths: List<Path>): Boolean

    /**
     * 添加任务，如果是文件夹会递归查询子然后传递
     */
    fun addTransfer(
        paths: List<Pair<Path, TransportTableModel.Attributes>>,
        mode: TransferMode
    ): CompletableFuture<Unit>

    /**
     * 手动指定传输到哪个目录
     */
    fun addTransfer(
        paths: List<Pair<Path, TransportTableModel.Attributes>>,
        targetWorkdir: Path,
        mode: TransferMode
    ): CompletableFuture<Unit>

    /**
     * 添加高优先级的传输，当有多个高优先级起的时候则有序传输，该方法通常用于编辑目的
     *
     * @return id
     */
    fun addHighTransfer(source: Path, target: Path): String

    fun addTransferListener(listener: TransferListener): Disposable
}