package app.termora.transport

import app.termora.Disposable
import org.apache.commons.io.IOUtils
import org.apache.commons.net.io.CopyStreamEvent
import org.apache.commons.net.io.CopyStreamListener
import org.apache.commons.net.io.Util
import org.apache.sshd.sftp.client.fs.SftpFileSystem
import org.eclipse.jgit.internal.transport.sshd.JGitClientSession
import org.slf4j.LoggerFactory
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

enum class TransportState {
    Waiting,
    Transporting,
    Done,
    Failed,
    Cancelled,
}

abstract class Transport(
    val name: String,
    // 源路径
    val source: Path,
    // 目标路径
    val target: Path,
    val sourceHolder: Disposable,
    val targetHolder: Disposable,
) : Disposable, Runnable {

    private val listeners = ArrayList<TransportListener>()

    @Volatile
    var state = TransportState.Waiting
        protected set(value) {
            field = value
            listeners.forEach { it.onTransportChanged(this) }
        }

    // 0 - 1
    var progress = 0.0
        protected set(value) {
            field = value
            listeners.forEach { it.onTransportChanged(this) }
        }

    /**
     * 要传输的大小
     */
    var size = -1L
        protected set

    /**
     * 已经传输的大小
     */
    var transferredSize = 0L
        protected set

    /**
     * 传输速度
     */
    open val speed get() = 0L

    open val getSourcePath by lazy {
        getFileSystemName(source.fileSystem) + ":" + source.toAbsolutePath().normalize().toString()
    }
    open val getTargetPath by lazy {
        getFileSystemName(target.fileSystem) + ":" + target.toAbsolutePath().normalize().toString()
    }


    fun addTransportListener(listener: TransportListener) {
        listeners.add(listener)
    }

    fun removeTransportListener(listener: TransportListener) {
        listeners.remove(listener)
    }

    override fun run() {
        if (state != TransportState.Waiting) {
            throw IllegalStateException("$name has already been started")
        }

        state = TransportState.Transporting
    }

    open fun stop() {
        if (state == TransportState.Waiting || state == TransportState.Transporting) {
            state = TransportState.Cancelled
        }
    }

    private fun getFileSystemName(fileSystem: FileSystem): String {
        if (fileSystem is SftpFileSystem) {
            val clientSession = fileSystem.session
            if (clientSession is JGitClientSession) {
                return clientSession.hostConfigEntry.host
            }
        }
        return "file"
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

    fun clear() {
        events.clear()
    }
}


/**
 * 文件传输
 */
class FileTransport(
    name: String, source: Path, target: Path,
    sourceHolder: Disposable, targetHolder: Disposable,
) : Transport(
    name, source, target, sourceHolder, targetHolder,
), CopyStreamListener {

    companion object {
        private val log = LoggerFactory.getLogger(FileTransport::class.java)
    }

    private var lastVisitTime = 0L
    private val input by lazy { Files.newInputStream(source) }
    private val output by lazy { Files.newOutputStream(target) }
    private val counter = SlidingWindowByteCounter()

    override val speed: Long
        get() = counter.getLastSecondBytes()


    override fun run() {

        try {
            super.run()
            doTransport()
            state = TransportState.Done
        } catch (e: Exception) {
            if (state == TransportState.Cancelled) {
                if (log.isWarnEnabled) {
                    log.warn("Transport $name is canceled")
                }
                return
            }
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
            state = TransportState.Failed
        } finally {
            counter.clear()
        }

    }

    override fun stop() {

        // 如果在传输中，那么直接关闭流
        if (state == TransportState.Transporting) {
            runCatching { IOUtils.closeQuietly(input) }
            runCatching { IOUtils.closeQuietly(output) }
        }

        super.stop()

        counter.clear()
    }

    private fun doTransport() {
        size = Files.size(source)
        try {
            Util.copyStream(
                input,
                output,
                Util.DEFAULT_COPY_BUFFER_SIZE * 8,
                size,
                this
            )
        } finally {
            IOUtils.closeQuietly(input, output)
        }
    }

    override fun bytesTransferred(event: CopyStreamEvent?) {
        throw UnsupportedOperationException()
    }

    override fun bytesTransferred(totalBytesTransferred: Long, bytesTransferred: Int, streamSize: Long) {

        if (state == TransportState.Cancelled) {
            throw IllegalStateException("$name has already been cancelled")
        }

        val now = System.currentTimeMillis()
        val progress = totalBytesTransferred * 1.0 / streamSize

        counter.addBytes(bytesTransferred.toLong(), now)

        if (now - lastVisitTime < 750) {
            if (progress < 1.0) {
                return
            }
        }

        this.transferredSize = totalBytesTransferred
        this.progress = progress
        lastVisitTime = now
    }
}

/**
 * 创建文件夹
 */
class DirectoryTransport(
    name: String, source: Path, target: Path,
    sourceHolder: Disposable,
    targetHolder: Disposable,
) : Transport(name, source, target, sourceHolder, targetHolder) {
    companion object {
        private val log = LoggerFactory.getLogger(DirectoryTransport::class.java)
    }


    override fun run() {

        try {
            super.run()
            if (!target.exists()) {
                Files.createDirectory(target)
            }
            state = TransportState.Done
        } catch (e: FileAlreadyExistsException) {
            if (log.isWarnEnabled) {
                log.warn("Directory $name already exists")
            }
            state = TransportState.Done
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
            state = TransportState.Failed
        }
    }
}