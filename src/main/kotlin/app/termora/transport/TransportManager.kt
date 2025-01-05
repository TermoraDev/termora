package app.termora.transport

import app.termora.Disposable
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

class TransportManager : Disposable {
    private val transports = Collections.synchronizedList(mutableListOf<Transport>())
    private val coroutineScope by lazy { CoroutineScope(Dispatchers.IO) }
    private val isProcessing = AtomicBoolean(false)
    private val listeners = mutableListOf<TransportListener>()
    private val listener = object : TransportListener {
        override fun onTransportAdded(transport: Transport) {
            listeners.forEach { it.onTransportAdded(transport) }
        }

        override fun onTransportRemoved(transport: Transport) {
            listeners.forEach { it.onTransportRemoved(transport) }
        }

        override fun onTransportChanged(transport: Transport) {
            listeners.forEach { it.onTransportChanged(transport) }
        }

    }

    companion object {
        private val log = LoggerFactory.getLogger(TransportManager::class.java)
    }

    fun getTransports(): List<Transport> = transports

    fun addTransport(transport: Transport) {
        synchronized(transports) {
            transport.addTransportListener(listener)
            if (transports.add(transport)) {
                listeners.forEach { it.onTransportAdded(transport) }
                if (isProcessing.compareAndSet(false, true)) {
                    coroutineScope.launch(Dispatchers.IO) { process() }
                }
            }
        }
    }

    fun removeTransport(transport: Transport) {
        synchronized(transports) {
            transport.stop()
            if (transports.remove(transport)) {
                listeners.forEach { it.onTransportRemoved(transport) }
            }
        }
    }

    fun removeAllTransports() {
        synchronized(transports) {
            while (transports.isNotEmpty()) {
                removeTransport(transports.last())
            }
        }
    }

    fun addTransportListener(listener: TransportListener) {
        listeners.add(listener)
    }

    fun removeTransportListener(listener: TransportListener) {
        listeners.remove(listener)
    }

    private suspend fun process() {
        var needDelay = false
        while (coroutineScope.isActive) {
            try {

                // 如果为空或者其中一个正在传输中那么挑过
                if (needDelay || transports.isEmpty()) {
                    needDelay = false
                    delay(250.milliseconds)
                    continue
                }

                val transport = synchronized(transports) {
                    var transport: Transport? = null
                    for (e in transports) {
                        if (e.state != TransportState.Waiting) {
                            continue
                        }

                        // 遇到传输中，那么直接跳过
                        if (e.state == TransportState.Transporting) {
                            needDelay = true
                            break
                        }

                        transport = e
                        break
                    }
                    return@synchronized transport
                }

                if (transport == null) {
                    continue
                }

                transport.run()

                // 成功之后 删除
                if (transport.state == TransportState.Done) {
                    // remove
                    removeTransport(transport)
                }

            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            }
        }
    }


    override fun dispose() {
        transports.clear()
        coroutineScope.cancel()
    }
}