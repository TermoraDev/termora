package app.termora.sftp

import app.termora.Disposable
import app.termora.I18n
import app.termora.assertEventDispatchThread
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import okio.withLock
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.jdesktop.swingx.treetable.DefaultMutableTreeTableNode
import org.jdesktop.swingx.treetable.DefaultTreeTableModel
import org.jdesktop.swingx.treetable.MutableTreeTableNode
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import javax.swing.SwingUtilities
import kotlin.collections.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds


class TransportTableModel(private val coroutineScope: CoroutineScope) :
    DefaultTreeTableModel(DefaultMutableTreeTableNode()), TransportManager, Disposable {

    val lock = ReentrantLock()

    private val transports = Collections.synchronizedMap(linkedMapOf<Long, TransportTreeTableNode>())
    private val reporter = SpeedReporter(coroutineScope)
    private var listeners = emptyArray<TransportListener>()
    private val activeTransports = linkedMapOf<Long, Job>()

    /**
     * 最多的平行任务
     */
    private val maxParallels = max(min(Runtime.getRuntime().availableProcessors(), 4), 1)

    companion object {
        private val log = LoggerFactory.getLogger(TransportTableModel::class.java)

        const val COLUMN_COUNT = 8

        const val COLUMN_NAME = 0
        const val COLUMN_STATUS = 1
        const val COLUMN_PROGRESS = 2
        const val COLUMN_SIZE = 3
        const val COLUMN_SOURCE_PATH = 4
        const val COLUMN_TARGET_PATH = 5
        const val COLUMN_SPEED = 6
        const val COLUMN_ESTIMATED_TIME = 7
    }

    init {
        setColumnIdentifiers(
            listOf(
                I18n.getString("termora.transport.jobs.table.name"),
                I18n.getString("termora.transport.jobs.table.status"),
                I18n.getString("termora.transport.jobs.table.progress"),
                I18n.getString("termora.transport.jobs.table.size"),
                I18n.getString("termora.transport.jobs.table.source-path"),
                I18n.getString("termora.transport.jobs.table.target-path"),
                I18n.getString("termora.transport.jobs.table.speed"),
                I18n.getString("termora.transport.jobs.table.estimated-time")
            )
        )
        coroutineScope.launch { run() }
    }


    override fun getRoot(): DefaultMutableTreeTableNode {
        return super.getRoot() as DefaultMutableTreeTableNode
    }

    override fun isCellEditable(node: Any?, column: Int): Boolean {
        return false
    }

    override fun addTransport(transport: Transport): Boolean {
        return lock.withLock {
            if (!transport.isRoot) {

                // 判断父是否存在
                if (!transports.containsKey(transport.parentId)) {
                    return@withLock false
                }

                // 检测状态
                if (!validGrandfatherStatus(transport)) {
                    changeStatus(transport, TransportStatus.Failed)
                }
            }

            val newNode = TransportTreeTableNode(transport)
            val parentId = transport.parentId
            val root = getRoot()
            val p = if (parentId == 0L || !transports.contains(parentId)) {
                root
            } else {
                transports.getValue(transport.parentId).apply { transport.parent = this.transport }
            }

            transports[transport.id] = newNode

            if ((transports.containsKey(parentId) || p == root) && transports.containsKey(transport.id)) {
                // 主线程加入节点
                SwingUtilities.invokeLater {
                    // 因为是异步的，父节点此时可能已经被移除了
                    if (p == root || transports.containsKey(parentId)) {
                        insertNodeInto(newNode, p, p.childCount)
                    } else {
                        removeTransport(transport.id)
                    }
                }
            }

            return@withLock true
        }


    }

    override fun getTransport(id: Long): Transport? {
        return transports[id]?.transport
    }

    override fun getTransports(pId: Long): List<Transport> {
        lock.withLock {
            if (pId == 0L) {
                return getRoot().children().toList().filterIsInstance<TransportTreeTableNode>()
                    .map { it.transport }
            }
            val p = transports[pId] ?: return emptyList()
            return p.children().toList().filterIsInstance<TransportTreeTableNode>()
                .map { it.transport }
        }
    }

    override fun getTransportCount(): Int {
        return transports.size
    }

    /**
     * 获取祖先的状态，如果祖先状态不正常，那么子直接定义为失败
     *
     * @return true 正常
     */
    private fun validGrandfatherStatus(transport: Transport): Boolean {
        lock.withLock {
            // 如果自己/父不正常，那么失败
            if (transport.isRoot) return transport.status != TransportStatus.Failed

            // 父不存在，那么直接定义失败
            val p = transports[transport.parentId] ?: return false

            // 父状态不正常，那么失败
            if (p.transport.status == TransportStatus.Failed) return false

            return validGrandfatherStatus(p.transport)
        }
    }

    override fun removeTransport(id: Long) {
        assertEventDispatchThread()

        lock.withLock {

            // ID 为空就是清空
            if (id <= 0) {

                // 定义为失败
                transports.forEach { changeStatus(it.value.transport, TransportStatus.Failed) }
                // 清除所有任务
                transports.clear()

                // 取消任务
                activeTransports.forEach { it.value.cancel() }
                activeTransports.clear()

                val root = getRoot()
                while (root.childCount > 0) {
                    val c = root.getChildAt(0)
                    if (c is MutableTreeTableNode) {
                        removeNodeFromParent(c)
                    }
                }

                return
            }

            val n = transports[id] ?: return
            val deletedIds = mutableListOf<Long>()
            n.visit { deletedIds.add(it.transport.id) }
            deletedIds.add(id)

            for (deletedId in deletedIds) {
                val node = transports[deletedId] ?: continue

                // 定义为失败
                changeStatus(node.transport, TransportStatus.Failed)
                if (deletedId == id) {
                    val p = if (node.transport.isRoot) root else transports[node.transport.parentId]
                    if (p != null) {
                        removeNodeFromParent(node)
                    }
                }

                // 尝试取消
                activeTransports[deletedId]?.cancel()

                transports.remove(deletedId)
            }

            // 如果不是成功，那么就是人工手动删除
            if (n.transport.status != TransportStatus.Done) {
                // 文件大小减去尚未传输的
                n.transport.reportFilesize(-abs((n.transport.filesize.get() - n.transport.transferredFilesize.get())))
            }
        }
    }

    override fun addTransportListener(listener: TransportListener) {
        listeners += listener
    }

    override fun removeTransportListener(listener: TransportListener) {
        listeners = ArrayUtils.removeElement(listeners, listener)
    }

    private suspend fun run() {
        while (coroutineScope.isActive) {
            val nodes = getReadyTransport()
            if (nodes.isEmpty()) {
                delay((Random.nextInt(100, 250)).milliseconds)
                continue
            }

            // pre process
            val readyNodes = mutableListOf<TransportTreeTableNode>()
            for (node in nodes) {
                val transport = node.transport

                // 因为有可能返回刚刚清理的 Transport，如果不返回清理的 Transport 那么就只能返回 null，返回null就要等待 N 毫秒
                if (transport.status != TransportStatus.Ready) continue

                // 如果祖先状态异常，那么直接定义为失败
                if (!validGrandfatherStatus(transport)) {
                    changeStatus(transport, TransportStatus.Failed)
                    continue
                }

                // 进行中
                if (!changeStatus(transport, TransportStatus.Processing)) continue

                // 能走到这里表示准备好的任务
                readyNodes.add(node)
            }

            // 如果没有准备好的节点，那么跳过
            if (readyNodes.isEmpty()) continue

            // 激活中的任务
            val activeTransports = mutableMapOf<Long, Job>()

            // 同步传输
            for (node in readyNodes) {
                val transport = node.transport
                activeTransports[transport.id] = coroutineScope.launch { doTransport(node) }
            }

            // 设置为全局的
            lock.withLock {
                this.activeTransports.forEach { it.value.cancel() }
                this.activeTransports.clear()
                this.activeTransports.putAll(activeTransports)
            }

            try {
                // 等待所有任务
                activeTransports.values.joinAll()
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            }

        }
    }

    private suspend fun doTransport(node: TransportTreeTableNode) {
        val transport = node.transport

        try {
            // 传输
            transport.transport(reporter)
            // 变更状态，文件夹不需要变更状态，因为当文件夹下所有文件都成功时，文件夹自然会成功
            if (transport.isFile) {
                changeStatus(transport, TransportStatus.Done)
            }
        } catch (e: Exception) {

            // 记录异常
            transport.exception = ExceptionUtils.getRootCause(e)

            if (e is TransportStatusException) {
                if (log.isWarnEnabled) {
                    log.warn("{}: {}", transport.source.name, e.message)
                }
            } else if (log.isErrorEnabled) {
                log.error(e.message, e)
            }

            // 定义为失败
            changeStatus(transport, TransportStatus.Failed)

        } finally {

            // 从激活中移除
            if (lock.tryLock()) {
                try {
                    activeTransports.remove(transport.id)
                } finally {
                    lock.unlock()
                }
            }

            // 安全删除
            if (transport.status == TransportStatus.Done) {
                safeRemoveTransport(node)
            }

        }
    }

    private fun fireTransportEvent(transport: Transport) {
        for (listener in listeners) {
            listener.onTransportChanged(transport)
        }
    }


    private suspend fun safeRemoveTransport(node: TransportTreeTableNode) {
        withContext(Dispatchers.Swing) {
            lock.withLock {
                var n = node as TransportTreeTableNode?
                while (n != null) {
                    // 如果还有子，跳过
                    if (n.childCount != 0) break
                    // 如果文件夹还没扫描完，那么不处理
                    if (n.transport.isDirectory && !n.transport.isScanned) break
                    // 提前保存一下父
                    val p = n.parent as? TransportTreeTableNode
                    // 设置成功
                    changeStatus(n.transport, TransportStatus.Done)
                    // 删除
                    removeTransport(n.transport.id)
                    // 继续向上查找
                    n = p
                }

            }
        }
    }

    private suspend fun getReadyTransport(): List<TransportTreeTableNode> {
        val nodes = mutableListOf<TransportTreeTableNode>()
        val removeNodes = mutableListOf<TransportTreeTableNode>()

        lock.withLock {

            val stack = ArrayDeque<TransportTreeTableNode>()
            val root = getRoot()
            for (i in root.childCount - 1 downTo 0) {
                val child = root.getChildAt(i)
                if (child is TransportTreeTableNode) {
                    stack.addLast(child)
                }
            }

            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                val transport = node.transport

                // 如果父已经失败，那么自己也定义为失败，之所以定义失败要走下去是因为它的子也要定义为失败
                if (transport.parent?.status == TransportStatus.Failed) {
                    changeStatus(transport, TransportStatus.Failed)
                }

                // 这是一个比较特殊的情况，因为传输任务和文件夹扫描并不是一个线程。
                // 如果该文件夹最后一个任务传输任务完成后（已经尝试清理）这时候
                // 因为还没有“定义为扫描完毕”那么清理任务就会认为还在扫描，但是已经
                // 扫描完了，所以这里要执行一次清理。
                if (transport.isDirectory && transport.status == TransportStatus.Processing) {
                    if (node.childCount == 0 && transport.isScanned) {
                        removeNodes.add(node)
                        break
                    }
                }

                if (transport.status == TransportStatus.Ready) {
                    if (transport.isDirectory) {
                        // 文件夹不允许和文件作为并行任务
                        if (nodes.isNotEmpty()) break
                        // 加入任务立即退出
                        nodes.add(node)
                        break
                    } else if (transport.isFile) {
                        // 如果要准备加入的并行任务不是一个父，那么不允许
                        if (nodes.isNotEmpty() && nodes.last().transport.parentId != transport.parentId) break
                        // 加入任务
                        nodes.add(node)
                        // 如果超出了最大
                        if (nodes.size >= maxParallels) break
                    }
                }


                // 文件不可能有子
                if (transport.isFile) {
                    continue
                }


                for (i in node.childCount - 1 downTo 0) {
                    val child = node.getChildAt(i)
                    if (child is TransportTreeTableNode) {
                        stack.addLast(child)
                    }
                }
            }

        }

        // 如果有要清理的节点，那么直接返回清理的节点
        if (removeNodes.isNotEmpty()) {
            removeNodes.forEach { safeRemoveTransport(it) }
            return removeNodes
        }

        return nodes
    }

    private fun changeStatus(transport: Transport, status: TransportStatus): Boolean {
        return transport.changeStatus(status).apply { if (this) fireTransportEvent(transport) }
    }

    override fun dispose() {
        lock.withLock {
            // remove all
            removeTransport(0L)
            coroutineScope.cancel()
        }
    }
}