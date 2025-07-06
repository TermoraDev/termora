package app.termora.transfer

import app.termora.*
import app.termora.protocol.PathHandler
import app.termora.protocol.PathHandlerRequest
import app.termora.protocol.TransferProtocolProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.awt.Window
import java.nio.file.FileSystem
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

internal class ReconnectableTransportSupportLoader(private val owner: Window, private val host: Host) :
    TransportSupportLoader {
    companion object {
        private val log = LoggerFactory.getLogger(ReconnectableTransportSupportLoader::class.java)
    }

    private val mutex = Mutex()
    private val reference = AtomicReference<MyTransportSupport>()

    private var support: MyTransportSupport?
        set(value) = reference.set(value)
        get() = reference.get()

    override suspend fun getTransportSupport(): TransportSupport {
        mutex.withLock {
            var c = support
            if (c != null) {
                if (c.getFileSystem().isOpen) {
                    return c
                }
                if (log.isWarnEnabled) {
                    log.warn("Host {} has been disconnected and will reconnect soon", host.name)
                }
                support = null
                Disposer.dispose(c)
            }
            c = connect().also { support = it }
            return c
        }
    }

    override fun getSyncTransportSupport(): TransportSupport {
        assertEventDispatchThread()
        val c = support
        if (c == null) throw IllegalStateException("No transport support")
        return c
    }

    override fun isLoaded(): Boolean {
        assertEventDispatchThread()
        return support != null
    }

    override fun isOpened(): Boolean {
        if (isLoaded().not()) return false
        val c = support ?: return false
        return c.getFileSystem().isOpen
    }

    override fun dispose() {
        val c = support
        if (c != null) {
            Disposer.dispose(c)
        }
    }

    private fun connect(): MyTransportSupport {
        val provider = TransferProtocolProvider.valueOf(host.protocol)
        if (provider == null) {
            throw IllegalStateException(I18n.getString("termora.protocol.not-supported", host.protocol))
        }
        val handler = provider.createPathHandler(PathHandlerRequest(host, owner))
        return MyTransportSupport(handler)
    }


    private inner class MyTransportSupport(private val handler: PathHandler) : TransportSupport, Disposable {

        init {
            Disposer.register(this, handler)
        }

        override fun getFileSystem(): FileSystem {
            return handler.fileSystem
        }

        override fun getDefaultPath(): Path {
            return handler.path
        }


    }
}