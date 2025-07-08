package app.termora.plugin.internal.ssh

import app.termora.*
import app.termora.keyboardinteractive.TerminalUserInteraction
import app.termora.keymgr.OhKeyPairKeyPairProvider
import app.termora.terminal.TerminalSize
import app.termora.x11.X11ChannelFactory
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.util.FontUtils
import com.formdev.flatlaf.util.SystemInfo
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.sshd.client.ClientBuilder
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.auth.password.UserAuthPasswordFactory
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.config.hosts.HostConfigEntry
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver
import org.apache.sshd.client.config.hosts.KnownHostEntry
import org.apache.sshd.client.kex.DHGClient
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier
import org.apache.sshd.client.keyverifier.ModifiedServerKeyAcceptor
import org.apache.sshd.client.keyverifier.ServerKeyVerifier
import org.apache.sshd.client.session.ClientProxyConnector
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.client.session.ClientSessionImpl
import org.apache.sshd.client.session.SessionFactory
import org.apache.sshd.common.AttributeRepository
import org.apache.sshd.common.SshException
import org.apache.sshd.common.channel.ChannelFactory
import org.apache.sshd.common.channel.PtyChannelConfiguration
import org.apache.sshd.common.channel.PtyChannelConfigurationHolder
import org.apache.sshd.common.cipher.CipherNone
import org.apache.sshd.common.compression.BuiltinCompressions
import org.apache.sshd.common.config.keys.KeyRandomArt
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.future.CloseFuture
import org.apache.sshd.common.future.SshFutureListener
import org.apache.sshd.common.global.KeepAliveHandler
import org.apache.sshd.common.io.IoConnectFuture
import org.apache.sshd.common.io.IoConnector
import org.apache.sshd.common.io.IoServiceEventListener
import org.apache.sshd.common.io.IoSession
import org.apache.sshd.common.kex.BuiltinDHFactories
import org.apache.sshd.common.keyprovider.KeyIdentityProvider
import org.apache.sshd.common.session.Session
import org.apache.sshd.common.session.SessionListener
import org.apache.sshd.common.signature.BuiltinSignatures
import org.apache.sshd.common.util.net.SshdSocketAddress
import org.apache.sshd.core.CoreModuleProperties
import org.apache.sshd.server.forward.AcceptAllForwardingFilter
import org.apache.sshd.server.forward.RejectAllForwardingFilter
import org.eclipse.jgit.internal.transport.sshd.JGitClientSession
import org.eclipse.jgit.internal.transport.sshd.JGitSshClient
import org.eclipse.jgit.internal.transport.sshd.agent.JGitSshAgentFactory
import org.eclipse.jgit.internal.transport.sshd.agent.connector.PageantConnector
import org.eclipse.jgit.internal.transport.sshd.agent.connector.UnixDomainSocketConnector
import org.eclipse.jgit.internal.transport.sshd.proxy.AbstractClientProxyConnector
import org.eclipse.jgit.internal.transport.sshd.proxy.HttpClientConnector
import org.eclipse.jgit.internal.transport.sshd.proxy.Socks5ClientConnector
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.SshConstants
import org.eclipse.jgit.transport.sshd.IdentityPasswordProvider
import org.eclipse.jgit.transport.sshd.agent.ConnectorFactory
import org.slf4j.LoggerFactory
import java.awt.Font
import java.awt.Window
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.file.Path
import java.nio.file.Paths
import java.security.PublicKey
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*
import kotlin.math.max

@Suppress("CascadeIf")
object SshClients {

    val HOST_KEY = AttributeRepository.AttributeKey<Host>()

    private val timeout = Duration.ofSeconds(30)
    private val hostManager get() = HostManager.Companion.getInstance()
    private val log by lazy { LoggerFactory.getLogger(SshClients::class.java) }

    /**
     * 打开一个 Shell
     */
    fun openShell(
        host: Host,
        size: TerminalSize,
        session: ClientSession,
    ): ChannelShell {


        val configuration = PtyChannelConfiguration()
        configuration.ptyColumns = size.cols
        configuration.ptyLines = size.rows
        configuration.ptyType = "xterm-256color"

        val env = mutableMapOf<String, String>()
        env["TERM"] = configuration.ptyType
        env.putAll(host.options.envs())

        val channel = session.createShellChannel(configuration, env)
        if (host.options.enableX11Forwarding) {
            if (channel is app.termora.x11.ChannelShell) {
                channel.xForwarding = true
            }
        }

        if (!channel.open().verify(timeout).await()) {
            throw SshException("Failed to open Shell")
        }

        return channel

    }

    /**
     * 执行一个命令
     *
     * @return first: exitCode , second: response
     */
    fun execChannel(
        session: ClientSession,
        command: String
    ): Pair<Int, String> {

        val baos = ByteArrayOutputStream()
        val channel = session.createExecChannel(command)
        channel.out = baos

        if (channel.open().verify(timeout).await(timeout)) {
            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), timeout)
        }

        IOUtils.closeQuietly(channel)

        if (channel.exitStatus == null) {
            return Pair(-1, baos.toString())
        }

        return Pair(channel.exitStatus, baos.toString())

    }

    /**
     * 打开一个会话
     */
    fun openSession(host: Host, client: SshClient): ClientSession {
        val h = hostManager.getHost(host.id) ?: host

        // 如果没有跳板机直接连接
        if (h.options.jumpHosts.isEmpty()) {
            return doOpenSession(h, client)
        }

        val jumpHosts = mutableListOf<Host>()
        val hosts = HostManager.Companion.getInstance().hosts().associateBy { it.id }
        for (jumpHostId in h.options.jumpHosts) {
            val e = hosts[jumpHostId]
            if (e == null) {
                if (log.isWarnEnabled) {
                    log.warn("Failed to find jump host: $jumpHostId")
                }
                continue
            }
            jumpHosts.add(e)
        }

        // 最后一跳是目标机器
        jumpHosts.add(h)

        val sessions = mutableListOf<ClientSession>()
        for (i in 0 until jumpHosts.size) {
            val currentHost = jumpHosts[i]
            sessions.add(doOpenSession(currentHost, client, i != 0))

            // 如果有下一跳
            if (i < jumpHosts.size - 1) {
                val nextHost = jumpHosts[i + 1]
                // 通过 currentHost 的 Session 将远程端口映射到本地
                val address = sessions.last().startLocalPortForwarding(
                    SshdSocketAddress.LOCALHOST_ADDRESS,
                    SshdSocketAddress(nextHost.host, nextHost.port),
                )
                if (log.isInfoEnabled) {
                    log.info("jump host: ${currentHost.host}:${currentHost.port} , next host: ${nextHost.host}:${nextHost.port} , local address: ${address.hostName}:${address.port}")
                }
                // 映射完毕之后修改Host和端口
                jumpHosts[i + 1] =
                    nextHost.copy(host = address.hostName, port = address.port)
            }
        }

        return sessions.last()
    }

    fun isMiddleware(session: ClientSession): Boolean {
        if (session is JGitClientSession) {
            if (session.hostConfigEntry.properties["Middleware"]?.toBoolean() == true) {
                return true
            }
        }
        return false
    }


    /**
     * @param middleware 如果为 true 表示是跳板
     */
    private fun doOpenSession(host: Host, client: SshClient, middleware: Boolean = false): ClientSession {
        val entry = HostConfigEntry()
        entry.port = host.port
        entry.username = host.username
        entry.hostName = host.host
        entry.setProperty("Middleware", middleware.toString())
        entry.setProperty("Host", host.id)

        // 设置代理
//        configureProxy(entry, host, client)

        // ssh-agent
        if (host.authentication.type == AuthenticationType.SSHAgent) {
            if (SystemInfo.isMacOS) {
                val file = FileUtils.getFile(Application.getBaseDataDir(), "config", "ssh-agent.sock")
                if (file.exists()) {
                    entry.setProperty(SshConstants.IDENTITY_AGENT, file.absolutePath)
                }
            }
            if (entry.getProperty(SshConstants.IDENTITY_AGENT).isNullOrBlank()) {
                if (host.authentication.password.isNotBlank())
                    entry.setProperty(SshConstants.IDENTITY_AGENT, host.authentication.password)
                else if (SystemInfo.isWindows)
                    entry.setProperty(SshConstants.IDENTITY_AGENT, PageantConnector.DESCRIPTOR.identityAgent)
                else
                    entry.setProperty(SshConstants.IDENTITY_AGENT, UnixDomainSocketConnector.DESCRIPTOR.identityAgent)
            }
        }

        val session = client.connect(entry).verify(timeout).session
        if (host.authentication.type == AuthenticationType.Password) {
            if (StringUtils.isNotBlank(host.authentication.password))
                session.addPasswordIdentity(host.authentication.password)
        } else if (host.authentication.type == AuthenticationType.PublicKey) {
            session.keyIdentityProvider = OhKeyPairKeyPairProvider(host.authentication.password)
        }

        if (host.options.enableX11Forwarding) {
            val segments = host.options.x11Forwarding.split(":")
            if (segments.size == 2) {
                val x11Host = segments[0]
                val x11Port = segments[1].toIntOrNull()
                if (x11Port != null) {
                    CoreModuleProperties.X11_BIND_HOST.set(session, x11Host)
                    CoreModuleProperties.X11_BASE_PORT.set(session, 6000 + x11Port)
                }
            }
        }

        try {
            if (!session.auth().verify(timeout).await(timeout)) {
                throw SshException("Authentication failed")
            }
        } catch (e: Exception) {
            if (e !is SshException || e.disconnectCode != org.apache.sshd.common.SshConstants.SSH2_DISCONNECT_NO_MORE_AUTH_METHODS_AVAILABLE) throw e
            val owner = client.properties["owner"] as Window? ?: throw e
            val askUserInfo = ask(host, entry, owner) ?: throw e
            if (askUserInfo.authentication.type == AuthenticationType.No) throw e
            return doOpenSession(
                host.copy(
                    authentication = askUserInfo.authentication,
                    username = askUserInfo.username
                ), client
            )
        }

        session.setAttribute(HOST_KEY, host)

        return session
    }

    fun openTunneling(session: ClientSession, host: Host, tunneling: Tunneling): SshdSocketAddress {

        val sshdSocketAddress = if (tunneling.type == TunnelingType.Local) {
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
        } else {
            SshdSocketAddress.LOCALHOST_ADDRESS
        }

        if (log.isInfoEnabled) {
            log.info(
                "SSH [{}] started {} port forwarding. host: {} , port: {}",
                host.name,
                tunneling.name,
                sshdSocketAddress.hostName,
                sshdSocketAddress.port
            )
        }

        return sshdSocketAddress
    }

    fun openClient(host: Host, owner: Window): SshClient {
        val h = hostManager.getHost(host.id) ?: host
        val client = openClient(h)
        client.userInteraction = TerminalUserInteraction(owner)
        client.serverKeyVerifier = DialogServerKeyVerifier(owner)
        client.properties["owner"] = owner
        return client
    }

    /**
     * 打开一个客户端
     */
    fun openClient(host: Host): SshClient {
        val builder = ClientBuilder.builder()
        builder.globalRequestHandlers(listOf(KeepAliveHandler.INSTANCE))
            .factory { MyJGitSshClient() }

        val keyExchangeFactories = ClientBuilder.setUpDefaultKeyExchanges(true).toMutableList()

        // https://github.com/TermoraDev/termora/issues/123
        @Suppress("DEPRECATION")
        keyExchangeFactories.addAll(
            listOf(
                DHGClient.newFactory(BuiltinDHFactories.dhg1),
                DHGClient.newFactory(BuiltinDHFactories.dhg14),
                DHGClient.newFactory(BuiltinDHFactories.dhgex),
            )
        )
        builder.keyExchangeFactories(keyExchangeFactories)

        val compressionFactories = ClientBuilder.setUpDefaultCompressionFactories(true).toMutableList()
        for (compression in listOf(
            BuiltinCompressions.none,
            BuiltinCompressions.zlib,
            BuiltinCompressions.delayedZlib
        )) {
            if (compressionFactories.contains(compression)) continue
            compressionFactories.add(compression)
        }
        builder.compressionFactories(compressionFactories)

        val signatureFactories = ClientBuilder.setUpDefaultSignatureFactories(true).toMutableList()
        for (signature in BuiltinSignatures.entries) {
            if (signatureFactories.contains(signature)) continue
            signatureFactories.add(signature)
        }
        builder.signatureFactories(signatureFactories)

        if (host.tunnelings.isEmpty() && host.options.jumpHosts.isEmpty()) {
            builder.forwardingFilter(RejectAllForwardingFilter.INSTANCE)
        } else {
            builder.forwardingFilter(AcceptAllForwardingFilter.INSTANCE)
        }

        builder.hostConfigEntryResolver(HostConfigEntryResolver.EMPTY)

        val channelFactories = mutableListOf<ChannelFactory>()
        channelFactories.addAll(ClientBuilder.DEFAULT_CHANNEL_FACTORIES)
        channelFactories.add(X11ChannelFactory.Companion.INSTANCE)
        builder.channelFactories(channelFactories)

        val sshClient = builder.build() as JGitSshClient

        // https://github.com/TermoraDev/termora/issues/180
        // JGit 会尝试读取本地的私钥或缓存的私钥
        sshClient.keyIdentityProvider = KeyIdentityProvider { mutableListOf() }

        // 设置优先级
        if (host.authentication.type == AuthenticationType.PublicKey || host.authentication.type == AuthenticationType.SSHAgent) {
            if (host.authentication.type == AuthenticationType.SSHAgent) {
                // ssh-agent
                sshClient.agentFactory = JGitSshAgentFactory(ConnectorFactory.getDefault(), null)
            }
            CoreModuleProperties.PREFERRED_AUTHS.set(
                sshClient,
                listOf(
                    UserAuthPasswordFactory.PUBLIC_KEY,
                    UserAuthPasswordFactory.PASSWORD,
                    UserAuthPasswordFactory.KB_INTERACTIVE
                ).joinToString(",")
            )
        } else {
            CoreModuleProperties.PREFERRED_AUTHS.set(
                sshClient,
                listOf(
                    UserAuthPasswordFactory.PASSWORD,
                    UserAuthPasswordFactory.PUBLIC_KEY,
                    UserAuthPasswordFactory.KB_INTERACTIVE
                ).joinToString(",")
            )
        }


        val heartbeatInterval = max(host.options.heartbeatInterval, 3)
        CoreModuleProperties.HEARTBEAT_INTERVAL.set(sshClient, Duration.ofSeconds(heartbeatInterval.toLong()))
        CoreModuleProperties.ALLOW_DHG1_KEX_FALLBACK.set(sshClient, true)

        sshClient.setKeyPasswordProviderFactory { IdentityPasswordProvider(CredentialsProvider.getDefault()) }

        sshClient.start()
        return sshClient
    }


    private data class AskUserInfo(val username: String, val authentication: Authentication)

    private fun ask(host: Host, entry: HostConfigEntry, owner: Window): AskUserInfo? {
        val ref = AtomicReference<AskUserInfo>(null)

        SwingUtilities.invokeAndWait {
            val dialog = RequestAuthenticationDialog(owner, host)
            dialog.setLocationRelativeTo(owner)
            val authentication = dialog.getAuthentication()
            ref.set(AskUserInfo(dialog.getUsername(), authentication))

            // save
            if (dialog.isRemembered()) {
                // fix https://github.com/TermoraDev/termora/issues/609
                val hostId = entry.getProperty("Host", host.id)
                val h = hostManager.getHost(hostId)
                if (h != null) {
                    hostManager.addHost(
                        h.copy(
                            authentication = authentication,
                            username = dialog.getUsername(),
                        )
                    )
                }
            }
        }
        return ref.get()
    }

    private class MyDialogServerKeyVerifier(private val owner: Window) : ServerKeyVerifier, ModifiedServerKeyAcceptor {
        override fun verifyServerKey(
            clientSession: ClientSession,
            remoteAddress: SocketAddress,
            serverKey: PublicKey
        ): Boolean {
            return true
        }

        override fun acceptModifiedServerKey(
            clientSession: ClientSession?,
            remoteAddress: SocketAddress?,
            entry: KnownHostEntry?,
            expected: PublicKey?,
            actual: PublicKey?
        ): Boolean {
            val result = AtomicBoolean(false)
            SwingUtilities.invokeAndWait { result.set(ask(remoteAddress, expected, actual) == JOptionPane.OK_OPTION) }
            return result.get()
        }

        private fun ask(
            remoteAddress: SocketAddress?,
            expected: PublicKey?,
            actual: PublicKey?
        ): Int {
            val formMargin = "7dlu"
            val layout = FormLayout(
                "default:grow",
                "pref, 12dlu, pref, 4dlu, pref, 2dlu, pref, $formMargin, pref, $formMargin, pref, pref, 12dlu, pref"
            )

            val errorColor = if (FlatLaf.isLafDark()) UIManager.getColor("Component.warning.focusedBorderColor") else
                UIManager.getColor("Component.error.focusedBorderColor")
            val font = FontUtils.getCompositeFont("JetBrains Mono", Font.PLAIN, 12)
            val artBox = Box.createHorizontalBox()
            artBox.add(Box.createHorizontalGlue())
            val expectedBox = Box.createVerticalBox()
            for (line in KeyRandomArt(expected).toString().lines()) {
                val label = JLabel(line)
                label.font = font
                expectedBox.add(label)
            }
            artBox.add(expectedBox)
            artBox.add(Box.createHorizontalGlue())
            val actualBox = Box.createVerticalBox()
            for (line in KeyRandomArt(actual).toString().lines()) {
                val label = JLabel(line)
                label.foreground = errorColor
                label.font = font
                actualBox.add(label)
            }
            artBox.add(actualBox)
            artBox.add(Box.createHorizontalGlue())

            var rows = 1
            val step = 2

            // @formatter:off
            val address = remoteAddress.toString().replace("/", StringUtils.EMPTY)
            val panel = FormBuilder.create().layout(layout)
                .add("<html><b>${I18n.getString("termora.host.modified-server-key.title", address)}</b></html>").xy(1, rows).apply { rows += step }
                .add("${I18n.getString("termora.host.modified-server-key.thumbprint")}:").xy(1, rows).apply { rows += step }
                .add("  ${I18n.getString("termora.host.modified-server-key.expected")}: ${KeyUtils.getFingerPrint(expected)}").xy(1, rows).apply { rows += step }
                .add("<html>&nbsp;&nbsp;${I18n.getString("termora.host.modified-server-key.actual")}: <font color=rgb(${errorColor.red},${errorColor.green},${errorColor.blue})>${KeyUtils.getFingerPrint(actual)}</font></html>").xy(1, rows).apply { rows += step }
                .addSeparator(StringUtils.EMPTY).xy(1, rows).apply { rows += step }
                .add(artBox).xy(1, rows).apply { rows += step }
                .addSeparator(StringUtils.EMPTY).xy(1, rows).apply { rows += 1 }
                .add(I18n.getString("termora.host.modified-server-key.are-you-sure")).xy(1, rows).apply { rows += step }
                .build()
            // @formatter:on

            return OptionPane.showConfirmDialog(
                owner,
                panel,
                "SSH Security Warning",
                messageType = JOptionPane.WARNING_MESSAGE,
                optionType = JOptionPane.OK_CANCEL_OPTION
            )

        }
    }

    private class DialogServerKeyVerifier(
        owner: Window,
    ) : KnownHostsServerKeyVerifier(
        MyDialogServerKeyVerifier(owner),
        Paths.get(Application.getBaseDataDir().absolutePath, "known_hosts")
    ) {
        init {
            modifiedServerKeyAcceptor = delegateVerifier as ModifiedServerKeyAcceptor
        }

        override fun updateKnownHostsFile(
            clientSession: ClientSession?,
            remoteAddress: SocketAddress?,
            serverKey: PublicKey?,
            file: Path?,
            knownHosts: Collection<HostEntryPair?>?
        ): KnownHostEntry? {
            if (clientSession is JGitClientSession) {
                if (isMiddleware(clientSession)) {
                    return null
                }
            }
            return super.updateKnownHostsFile(clientSession, remoteAddress, serverKey, file, knownHosts)
        }
    }


    @Suppress("UNCHECKED_CAST")
    private class MyJGitSshClient : JGitSshClient() {
        companion object {
            private val HOST_CONFIG_ENTRY: AttributeRepository.AttributeKey<HostConfigEntry> by lazy {
                JGitSshClient::class.java.getDeclaredField("HOST_CONFIG_ENTRY").apply { isAccessible = true }
                    .get(null) as AttributeRepository.AttributeKey<HostConfigEntry>
            }
            private const val CLIENT_PROXY_CONNECTOR = "ClientProxyConnectorId"
        }

        private val sshClient = this
        private val clientProxyConnectors = ConcurrentHashMap<String, ClientProxyConnector>()


        override fun createConnector(): IoConnector {
            return MyIoConnector(this, super.createConnector())
        }

        override fun createSessionFactory(): SessionFactory {
            return object : SessionFactory(sshClient) {
                override fun doCreateSession(ioSession: IoSession): ClientSessionImpl {
                    return object : JGitClientSession(sshClient, ioSession) {
                        override fun getClientProxyConnector(): ClientProxyConnector? {
                            val entry = getAttribute(HOST_CONFIG_ENTRY) ?: return null
                            val clientProxyConnectorId = entry.getProperty(CLIENT_PROXY_CONNECTOR) ?: return null
                            val clientProxyConnector = sshClient.clientProxyConnectors[clientProxyConnectorId]

                            if (clientProxyConnector != null) {
                                addSessionListener(object : SessionListener {
                                    override fun sessionClosed(session: Session) {
                                        clientProxyConnectors.remove(clientProxyConnectorId)
                                    }
                                })
                            }

                            return clientProxyConnector
                        }

                        override fun createShellChannel(
                            ptyConfig: PtyChannelConfigurationHolder?,
                            env: MutableMap<String, *>?
                        ): ChannelShell {
                            if (inCipher is CipherNone || outCipher is CipherNone)
                                throw IllegalStateException("Interactive channels are not supported with none cipher")
                            val channel = app.termora.x11.ChannelShell(ptyConfig, env)
                            val id = connectionService.registerChannel(channel)
                            if (log.isDebugEnabled) {
                                log.debug("createShellChannel({}) created id={} - PTY={}", this, id, ptyConfig)
                            }
                            return channel
                        }

                    }
                }
            }
        }

        override fun setClientProxyConnector(proxyConnector: ClientProxyConnector?) {
            throw UnsupportedOperationException()
        }

        private class MyIoConnector(private val sshClient: MyJGitSshClient, private val ioConnector: IoConnector) :
            IoConnector {
            override fun close(immediately: Boolean): CloseFuture {
                return ioConnector.close(immediately)
            }

            override fun addCloseFutureListener(listener: SshFutureListener<CloseFuture>?) {
                return ioConnector.addCloseFutureListener(listener)
            }

            override fun removeCloseFutureListener(listener: SshFutureListener<CloseFuture>?) {
                return ioConnector.removeCloseFutureListener(listener)
            }

            override fun isClosed(): Boolean {
                return ioConnector.isClosed
            }

            override fun isClosing(): Boolean {
                return ioConnector.isClosing
            }

            override fun getIoServiceEventListener(): IoServiceEventListener {
                return ioConnector.ioServiceEventListener
            }

            override fun setIoServiceEventListener(listener: IoServiceEventListener?) {
                return ioConnector.setIoServiceEventListener(listener)
            }

            override fun getManagedSessions(): MutableMap<Long, IoSession> {
                return ioConnector.managedSessions
            }

            override fun connect(
                targetAddress: SocketAddress,
                context: AttributeRepository?,
                localAddress: SocketAddress?
            ): IoConnectFuture {
                var tAddress = targetAddress
                val entry = context?.getAttribute(HOST_CONFIG_ENTRY)
                if (entry != null) {
                    val host = hostManager.getHost(entry.getProperty("Host") ?: StringUtils.EMPTY)
                    if (host != null) {
                        tAddress = configureProxy(entry, host, tAddress)
                    }
                }
                return ioConnector.connect(tAddress, context, localAddress)
            }

            private fun configureProxy(
                entry: HostConfigEntry,
                host: Host,
                targetAddress: SocketAddress
            ): SocketAddress {
                if (host.proxy.type == ProxyType.No) return targetAddress
                val address = targetAddress as? InetSocketAddress ?: return targetAddress
                if (address.hostString == (SshdSocketAddress.LOCALHOST_IPV4)) return targetAddress

                // 获取代理连接器
                val clientProxyConnector = getClientProxyConnector(host, address) ?: return targetAddress

                val id = randomUUID()
                entry.setProperty(CLIENT_PROXY_CONNECTOR, id)
                sshClient.clientProxyConnectors[id] = clientProxyConnector

                return InetSocketAddress(host.proxy.host, host.proxy.port)
            }

            private fun getClientProxyConnector(
                host: Host,
                remoteAddress: InetSocketAddress
            ): AbstractClientProxyConnector? {
                if (host.proxy.type == ProxyType.HTTP) {
                    return HttpClientConnector(
                        InetSocketAddress(host.proxy.host, host.proxy.port),
                        remoteAddress,
                        host.proxy.username.ifBlank { null },
                        if (host.proxy.password.isBlank()) null else host.proxy.password.toCharArray()
                    )
                } else if (host.proxy.type == ProxyType.SOCKS5) {
                    return Socks5ClientConnector(
                        InetSocketAddress(host.proxy.host, host.proxy.port),
                        remoteAddress,
                        host.proxy.username.ifBlank { null },
                        if (host.proxy.password.isBlank()) null else host.proxy.password.toCharArray()
                    )
                }
                return null
            }

        }


    }

}