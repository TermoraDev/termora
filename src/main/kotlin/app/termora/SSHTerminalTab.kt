package app.termora

import app.termora.addons.zmodem.ZModemPtyConnectorAdaptor
import app.termora.keyboardinteractive.TerminalUserInteraction
import app.termora.terminal.ControlCharacters
import app.termora.terminal.DataKey
import app.termora.terminal.PtyConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.apache.commons.io.Charsets
import org.apache.commons.lang3.StringUtils
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.SshConstants
import org.apache.sshd.common.channel.Channel
import org.apache.sshd.common.channel.ChannelListener
import org.apache.sshd.common.session.Session
import org.apache.sshd.common.session.SessionListener
import org.apache.sshd.common.session.SessionListener.Event
import org.apache.sshd.common.util.net.SshdSocketAddress
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import javax.swing.JComponent
import javax.swing.SwingUtilities


class SSHTerminalTab(host: Host) : PtyHostTerminalTab(host) {
    companion object {
        private val log = LoggerFactory.getLogger(PtyHostTerminalTab::class.java)
    }

    private val mutex = Mutex()

    private var sshClient: SshClient? = null
    private var sshSession: ClientSession? = null
    private var sshChannelShell: ChannelShell? = null

    init {
        terminalPanel.dropFiles = false
    }

    override fun getJComponent(): JComponent {
        return terminalPanel
    }


    override fun canReconnect(): Boolean {
        return !mutex.isLocked
    }


    override suspend fun openPtyConnector(): PtyConnector {
        if (mutex.tryLock()) {
            try {
                return doOpenPtyConnector()
            } finally {
                mutex.unlock()
            }
        }
        throw IllegalStateException("Opening PtyConnector")
    }


    private suspend fun doOpenPtyConnector(): PtyConnector {

        // 连接提示
        withContext(Dispatchers.Swing) {
            // clear screen
            terminal.clearScreen()
            // hide cursor
            terminalModel.setData(DataKey.ShowCursor, false)
            // print
            terminal.write("SSH client is opening...\r\n")
        }

        val client = SshClients.openClient(host).also { sshClient = it }
        // keyboard interactive
        client.userInteraction = TerminalUserInteraction(SwingUtilities.getWindowAncestor(terminalPanel))

        val sessionListener = MySessionListener()
        val channelListener = MyChannelListener()

        withContext(Dispatchers.Swing) { terminal.write("SSH client opened successfully.\r\n\r\n") }

        client.addSessionListener(sessionListener)
        client.addChannelListener(channelListener)

        val (session, channel) = try {
            val session = SshClients.openSession(host, client).also { sshSession = it }
            val channel = SshClients.openShell(
                host,
                terminalPanel.winSize(),
                session
            ).also { sshChannelShell = it }
            Pair(session, channel)
        } finally {
            client.removeSessionListener(sessionListener)
            client.removeChannelListener(channelListener)
        }

        // newline
        withContext(Dispatchers.Swing) {
            terminal.write("\r\n")
        }


        channel.addChannelListener(object : ChannelListener {
            override fun channelClosed(channel: Channel, reason: Throwable?) {
                coroutineScope.launch(Dispatchers.Swing) {
                    terminal.write("\r\n${ControlCharacters.ESC}[31m")
                    terminal.write("Channel has been disconnected.\r\n")
                    terminal.write("${ControlCharacters.ESC}[0m")
                    terminalModel.setData(DataKey.ShowCursor, false)
                }
            }
        })

        // 打开隧道
        openTunnelings(session, host)

        // 隐藏提示
        withContext(Dispatchers.Swing) {
            // clear screen
            terminal.clearScreen()
            // show cursor
            terminalModel.setData(DataKey.ShowCursor, true)
        }

        return ptyConnectorFactory.decorate(
            ZModemPtyConnectorAdaptor(
                terminal,
                terminalPanel,
                ChannelShellPtyConnector(
                    channel,
                    charset = Charsets.toCharset(host.options.encoding, StandardCharsets.UTF_8)
                )
            )
        )
    }

    private suspend fun openTunnelings(session: ClientSession, host: Host) {
        if (host.tunnelings.isEmpty()) {
            return
        }

        for (tunneling in host.tunnelings) {
            if (tunneling.type == TunnelingType.Local) {
                session.startLocalPortForwarding(
                    SshdSocketAddress(tunneling.sourceHost, tunneling.sourcePort),
                    SshdSocketAddress(tunneling.destinationHost, tunneling.destinationPort)
                )
            } else if (tunneling.type == TunnelingType.Remote) {
                session.startRemotePortForwarding(
                    SshdSocketAddress(tunneling.sourceHost, tunneling.sourcePort),
                    SshdSocketAddress(tunneling.destinationHost, tunneling.destinationPort),
                )
            } else if (tunneling.type == TunnelingType.Dynamic) {
                session.startDynamicPortForwarding(
                    SshdSocketAddress(
                        tunneling.sourceHost,
                        tunneling.sourcePort
                    )
                )
            }

            if (log.isInfoEnabled) {
                log.info("SSH [{}] started {} port forwarding.", host.name, tunneling.name)
            }

            withContext(Dispatchers.Swing) {
                terminal.write("Start [${tunneling.name}] port forwarding successfully.\r\n")
            }
        }
    }


    override fun stop() {
        if (mutex.tryLock()) {
            try {
                super.stop()

                sshChannelShell?.close(true)
                sshSession?.disableSessionHeartbeat()
                sshSession?.disconnect(SshConstants.SSH2_DISCONNECT_BY_APPLICATION, StringUtils.EMPTY)
                sshSession?.close(true)
                sshClient?.close(true)

                sshChannelShell = null
                sshSession = null
                sshClient = null
            } finally {
                mutex.unlock()
            }
        }
    }


    private inner class MySessionListener : SessionListener, Disposable {
        override fun sessionEvent(session: Session, event: Event) {
            coroutineScope.launch {
                when (event) {
                    Event.KeyEstablished -> terminal.write("Session Key exchange successful.\r\n")
                    Event.Authenticated -> terminal.write("Session authentication successful.\r\n\r\n")
                    Event.KexCompleted -> terminal.write("Session KEX negotiation successful.\r\n")
                }
            }
        }

        override fun sessionEstablished(session: Session) {
            coroutineScope.launch { terminal.write("Session established.\r\n") }
        }

        override fun sessionCreated(session: Session?) {
            coroutineScope.launch { terminal.write("Session created.\r\n") }
        }


    }

    private inner class MyChannelListener : ChannelListener, Disposable {
        override fun channelOpenSuccess(channel: Channel) {
            coroutineScope.launch { terminal.write("Channel shell opened successfully.\r\n") }
        }

        override fun channelInitialized(channel: Channel) {
            coroutineScope.launch { terminal.write("Channel shell initialization successful.\r\n") }
        }

    }
}