package app.termora.sftp

import app.termora.database.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.apache.commons.io.IOUtils
import org.apache.commons.net.io.Util
import org.apache.commons.vfs2.FileObject
import org.slf4j.LoggerFactory
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class TransportStatus {
    Ready,
    Processing,
    Failed,
    Done,
}

/**
 * 传输单位：单个文件
 */
class Transport(
    /**
     * 唯一 ID
     */
    val id: Long = idGenerator.incrementAndGet(),

    /**
     * 是否是文件夹
     */
    val isDirectory: Boolean = false,

    /**
     * 父
     */
    val parentId: Long = 0,

    /**
     * 源
     */
    val source: FileObject,

    /**
     * 目标
     */
    var target: FileObject,
    /**
     * 仅对文件生效，切只有两个选项
     *
     * 1. [StandardOpenOption.APPEND]
     * 2. [StandardOpenOption.TRUNCATE_EXISTING]
     */
    var mode: StandardOpenOption = StandardOpenOption.TRUNCATE_EXISTING
) {

    companion object {
        val idGenerator = AtomicLong(0)
        private val exception = RuntimeException("Nothing")
        private val log = LoggerFactory.getLogger(Transport::class.java)
        private val isPreserveModificationTime get() = DatabaseManager.getInstance().sftp.preserveModificationTime
    }

    private val scanned by lazy { AtomicBoolean(false) }

    /**
     * 计数器
     */
    private val counter by lazy { SlidingWindowByteCounter() }

    /**
     * 父
     */
    var parent: Transport? = null
        set(value) {
            if (field != null) throw IllegalStateException("parent already exists")
            field = value
            // 上报大小
            reportFilesize(filesize.get())
        }

    /**
     * 文件大小，对于文件夹来说，文件大小是不确定的，它取决于文件夹下的文件
     */
    val filesize = AtomicLong(0)

    /**
     * 已经传输完成的文件大小
     */
    val transferredFilesize = AtomicLong(0)

    /**
     * 如果是文件夹，是否已经扫描完毕。如果已经扫描完毕，那么该文件夹传输完成后可以立即删除
     */
    val isScanned get() = scanned.get()

    val isFile = !isDirectory
    val isRoot = parentId == 0L

    /**
     * 获取最近一秒内的速度
     */
    val speed get() = counter.getLastSecondBytes()

    /**
     * 状态
     */
    @Volatile
    var status: TransportStatus = TransportStatus.Ready
        private set

    /**
     * 失败异常
     */
    var exception: Throwable = Transport.exception


    fun scanned() {
        scanned.compareAndSet(false, true)
    }

    fun changeStatus(status: TransportStatus): Boolean {
        synchronized(this) {
            if (status == TransportStatus.Processing) {
                if (this.status != TransportStatus.Ready) {
                    return false
                }
            } else if (status == TransportStatus.Failed || status == TransportStatus.Done) {
                if (this.status != TransportStatus.Ready && this.status != TransportStatus.Processing) {
                    return false
                }
            } else if (status == TransportStatus.Ready) {
                if (this.status != TransportStatus.Ready) {
                    return false
                }
            }

            this.status = status

            return true
        }
    }

    private val c = AtomicLong(0)

    /**
     * 开始传输
     */
    suspend fun transport(reporter: SpeedReporter) {

        if (isDirectory) {
            withContext(Dispatchers.IO) {
                try {
                    if (!target.exists()) {
                        target.createFolder()
                    }
                } catch (_: FileAlreadyExistsException) {
                    if (log.isWarnEnabled) {
                        log.warn("Directory ${target.name} already exists")
                    }
                } catch (e: Exception) {
                    exception = e
                    throw e
                }
            }
            return
        }

        withContext(Dispatchers.IO) {
            val input = source.content.inputStream
            val output = target.content.getOutputStream(mode == StandardOpenOption.APPEND)

            try {

                val buff = ByteArray(Util.DEFAULT_COPY_BUFFER_SIZE)
                var len: Int
                while (input.read(buff).also { len = it } != -1 && this.isActive) {

                    // 写入
                    output.write(buff, 0, len)

                    val size = len.toLong()
                    val now = System.currentTimeMillis()

                    // 上报传输的字节数量
                    reporter.report(this@Transport, size, now)

                    // 如果状态错误，那么可能已经取消了
                    if (status != TransportStatus.Processing) {
                        throw TransportStatusException("status is $status")
                    }

                }

            } finally {
                IOUtils.closeQuietly(input, output)
            }

            // 尝试修改时间
            preserveModificationTime()


        }

    }

    private fun preserveModificationTime() {
        // 设置修改时间
        if (isPreserveModificationTime) {
            target.content.lastModifiedTime = source.content.lastModifiedTime
        }
    }

    /**
     * 一层层上报文件大小
     */
    fun reportFilesize(bytes: Long) {
        val p = parent ?: return
        if (isRoot) return
        // 父状态不正常
        if (p.status == TransportStatus.Failed) return
        // 父的文件大小就是自己的文件大小
        p.filesize.addAndGet(bytes)
        // 递归上报
        p.reportFilesize(bytes)
    }

    /**
     * 一层层上报传输大小
     */
    fun reportTransferredFilesize(bytes: Long, time: Long) {
        var p = this as Transport?
        while (p != null) {
            // 记录上报的数量，用于统计速度
            if (bytes > 0) p.counter.addBytes(bytes, time)
            // 状态不正常
            if (p.status == TransportStatus.Failed) return
            // 父的传输文件大小就是自己的传输文件大小
            p.transferredFilesize.addAndGet(bytes)
            p = p.parent
            c.incrementAndGet()
        }
    }

}


private class SlidingWindowByteCounter {
    private val events = ConcurrentLinkedQueue<Pair<Long, Long>>()
    private val oneSecondInMillis = TimeUnit.SECONDS.toMillis(1)

    fun addBytes(bytes: Long, time: Long) {

        // 添加当前事件
        events.add(time to bytes)

        // 移除过期事件（超过 1 秒的记录）
        while (events.isNotEmpty() && events.peek().first < time - oneSecondInMillis) {
            events.poll()
        }

    }

    fun getLastSecondBytes(): Long {
        val currentTime = System.currentTimeMillis()
        // 累加最近 1 秒内的字节数
        return events.filter { it.first >= currentTime - oneSecondInMillis }
            .sumOf { it.second }
    }

}
