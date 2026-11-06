package app.termora.tunneling

import app.termora.*
import app.termora.plugin.internal.ssh.SshClients
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.slf4j.LoggerFactory
import java.awt.Window

/** All registry changes happen on the Swing event thread; network operations run on IO. */
internal object TunnelManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    private val log = LoggerFactory.getLogger(TunnelManager::class.java)
    private val entries = mutableMapOf<Pair<String, Tunneling>, Entry>()
    private var started = false
    private val reconciliation = javax.swing.Timer(1000) {
        val hosts = HostManager.getInstance().hosts().associateBy { it.id }
        entries.keys.toList().forEach { (hostId, tunnel) ->
            val host = hosts[hostId]
            if (host == null || tunnel !in host.tunnelings || !tunnel.enabled) {
                stop(Host(id = hostId, name = "", protocol = "SSH"), tunnel)
            }
        }
    }
    private class Entry {
        var state = "connecting"
        var error = ""
        var client: SshClient? = null
        var session: ClientSession? = null
    }

    init {
        Disposer.register(ApplicationScope.forApplicationScope(), object : Disposable {
            override fun dispose() {
                reconciliation.stop()
                entries.values.forEach { entry ->
                    entry.session?.close(true)
                    entry.client?.close(true)
                }
                entries.clear()
                scope.cancel()
            }
        })
    }

    fun status(host: Host, tunnel: Tunneling): String {
        val entry = entries[host.id to tunnel] ?: return I18n.getString("termora.tunnels.stopped")
        if (entry.state == "running" && entry.session?.isOpen != true) entry.state = "disconnected"
        return I18n.getString("termora.tunnels.${entry.state}") + entry.error.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
    }

    fun startAutomatically(owner: Window) {
        if (started) return
        started = true
        reconciliation.start()
        HostManager.getInstance().hosts().filter { it.protocol == "SSH" }.forEach { host ->
            host.tunnelings.filter { it.enabled && it.autoStart }.forEach { start(host, it, owner) }
        }
    }

    fun startHost(host: Host, owner: Window) {
        if (ConnectionSettings.unifiedTunnelsOnly) return
        val latest = HostManager.getInstance().getHost(host.id) ?: host
        latest.tunnelings.filter { it.enabled }.forEach { start(latest, it, owner) }
    }

    fun start(host: Host, tunnel: Tunneling, owner: Window) {
        if (!tunnel.enabled) return
        val key = host.id to tunnel
        val old = entries[key]
        if (old?.state == "connecting" || old?.session?.isOpen == true) return
        stop(host, tunnel)
        val entry = Entry()
        entries[key] = entry
        scope.launch {
            var client: SshClient? = null
            var session: ClientSession? = null
            try {
                client = SshClients.openClient(host, owner)
                val connectedClient = client
                withContext(Dispatchers.IO) {
                    session = SshClients.openSession(host, connectedClient)
                    SshClients.openTunneling(session!!, host, tunnel)
                }
                if (entries[key] !== entry) {
                    withContext(Dispatchers.IO) { session?.close(true); connectedClient.close(true) }
                } else {
                    entry.client = client
                    entry.session = session
                    entry.state = "running"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.IO) { session?.close(true); client?.close(true) }
                if (entries[key] === entry) {
                    entry.state = "failed"
                    entry.error = e.message ?: e.javaClass.simpleName
                }
                log.error("Tunnel startup failed for host {} and tunnel {}", host.id, tunnel.name, e)
            }
        }
    }

    fun stop(host: Host, tunnel: Tunneling) {
        val entry = entries.remove(host.id to tunnel) ?: return
        scope.launch(Dispatchers.IO) {
            try {
                entry.session?.close(true)
                entry.client?.close(true)
            } catch (e: Exception) {
                log.error("Tunnel shutdown failed", e)
            }
        }
    }
}
