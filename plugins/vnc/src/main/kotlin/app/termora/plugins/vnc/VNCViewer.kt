package app.termora.plugins.vnc

import app.termora.*
import com.glavsoft.exceptions.AuthenticationFailedException
import com.glavsoft.exceptions.UnsupportedProtocolVersionException
import com.glavsoft.exceptions.UnsupportedSecurityTypeException
import com.glavsoft.rfb.ClipboardController
import com.glavsoft.rfb.client.KeyEventMessage
import com.glavsoft.rfb.encoding.EncodingType
import com.glavsoft.rfb.protocol.Protocol
import com.glavsoft.rfb.protocol.ProtocolSettings
import com.glavsoft.transport.BaudrateMeter
import com.glavsoft.transport.Transport
import com.glavsoft.utils.Keymap
import com.glavsoft.viewer.settings.LocalMouseCursorShape
import com.glavsoft.viewer.settings.UiSettings
import com.glavsoft.viewer.swing.ClipboardControllerImpl
import com.glavsoft.viewer.swing.Surface
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.lang3.StringUtils
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.event.AWTEventListener
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.Executors
import javax.swing.*
import kotlin.math.max


class VNCViewer(private val host: Host) : JPanel(BorderLayout()), Disposable {
    private var socket: Socket? = null
    private var surface: Surface? = null
    private var protocol: Protocol? = null
    private var uiSettings: UiSettings? = null
    private var clipboardController: ClipboardController? = null

    private val layeredPane = LayeredPane()
    private val toolbar = MyToolbar()
    private val scrollPane = JScrollPane()

    private val executorService = Executors.newVirtualThreadPerTaskExecutor()
    private val coroutineDispatcher = executorService.asCoroutineDispatcher()
    private val coroutineScope = CoroutineScope(coroutineDispatcher)
    private val owner get() = SwingUtilities.getWindowAncestor(this)

    init {
        initView()
        initEvents()
    }

    fun initView() {
        scrollPane.border = BorderFactory.createEmptyBorder()

        layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER as Any)
        layeredPane.add(toolbar, JLayeredPane.PALETTE_LAYER as Any)
        add(layeredPane, BorderLayout.CENTER)
    }

    fun initEvents() {
        coroutineScope.launch {
            try {
                connect()
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Swing) {
                    OptionPane.showMessageDialog(
                        owner,
                        getErrorMessage(e),
                        messageType = JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }

        toolkit.addAWTEventListener(toolbar, AWTEvent.MOUSE_MOTION_EVENT_MASK)
    }

    private fun getErrorMessage(exception: Exception): String {
        val causes = generateSequence<Throwable>(exception) { it.cause }.toList()
        return when {
            causes.any { it is AuthenticationFailedException } ->
                I18n.getString("termora.vnc.error.authentication-failed")

            causes.any { it is UnsupportedProtocolVersionException } ->
                I18n.getString("termora.vnc.error.unsupported-protocol")

            causes.any { it is UnsupportedSecurityTypeException } ->
                I18n.getString("termora.vnc.error.unsupported-security")

            causes.any { it is SocketTimeoutException } ->
                I18n.getString("termora.vnc.error.timeout")

            causes.any { it is UnknownHostException } ->
                I18n.getString("termora.vnc.error.unknown-host")

            causes.any { it is ConnectException } ->
                I18n.getString("termora.vnc.error.connection-failed")

            else -> I18n.getString("termora.vnc.error.generic")
        }
    }

    private suspend fun connect() {
        withContext(Dispatchers.Swing) {
            surface?.let { scrollPane.remove(it) }
        }

        disconnect()

        val proxy = when (host.proxy.type) {
            ProxyType.HTTP -> Proxy(Proxy.Type.HTTP, InetSocketAddress(host.proxy.host, host.proxy.port))
            ProxyType.SOCKS5 -> Proxy(Proxy.Type.SOCKS, InetSocketAddress(host.proxy.host, host.proxy.port))
            else -> Proxy.NO_PROXY
        }

        val socket = Socket(proxy).also { this.socket = it }
        socket.keepAlive = true
        socket.connect(InetSocketAddress(host.host, host.port))
        socket.tcpNoDelay = true

        val transport = Transport(socket)
        transport.setBaudrateMeter(BaudrateMeter())
        val uiSettings = UiSettings().also { uiSettings = it }
        val protocolSettings = ProtocolSettings.getDefaultSettings()
        protocolSettings.preferredEncoding = EncodingType.ZRLE

        val protocol = Protocol(transport, { host.authentication.password }, protocolSettings)
            .also { this.protocol = it }
        val surface = Surface(protocol, 1.0, LocalMouseCursorShape.NO_CURSOR).also { this.surface = it }

        uiSettings.addListener(surface)
        protocolSettings.addListener(surface)

        val encoding = StringUtils.defaultIfBlank(host.options.encoding, "ISO-8859-1")
        val clipboardController = ClipboardControllerImpl(protocol, encoding).also { this.clipboardController = it }

        protocol.handshake()
        protocol.startNormalHandling({ disconnect() }, surface, clipboardController)

        executorService.execute(clipboardController)

        withContext(Dispatchers.Swing) {
            scrollPane.setViewportView(surface)
        }
    }

    private fun disconnect() {
        socket?.close()
        clipboardController?.setEnabled(false)

        clipboardController = null
        protocol = null
        surface = null
        socket = null
        uiSettings = null
    }

    override fun dispose() {
        disconnect()

        toolkit.removeAWTEventListener(toolbar)

        coroutineScope.cancel()
        coroutineDispatcher.close()
        executorService.shutdownNow()
    }

    private inner class MyToolbar : JToolBar(), AWTEventListener {
        private val zoomInBtn = JButton(MyIcons.zoomIn)
        private val zoomOutBtn = JButton(MyIcons.zoomOut)
        private val actualZoomBtn = JButton(MyIcons.actualZoom)
        private val fitContentBtn = JButton(Icons.fitContent)
        private val ctrlAltDelBtn = JButton(MyIcons.ctrlAltDel)

        var collapse = true

        init {
            initView()
            initEvents()
        }

        fun initView() {
            add(zoomInBtn)
            add(zoomOutBtn)
            add(actualZoomBtn)
            add(fitContentBtn)
            addSeparator()
            add(ctrlAltDelBtn)

            border = BorderFactory.createMatteBorder(0, 1, 1, 1, DynamicColor.BorderColor)
        }

        fun initEvents() {
            zoomInBtn.addActionListener(object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    uiSettings?.zoomIn()
                }
            })

            zoomOutBtn.addActionListener(object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    uiSettings?.zoomOut()
                }
            })

            actualZoomBtn.addActionListener(object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    uiSettings?.zoomAsIs()
                }
            })

            fitContentBtn.addActionListener(object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    val uiSettings = uiSettings ?: return
                    val protocol = protocol ?: return
                    uiSettings.zoomToFit(layeredPane.width, layeredPane.height, protocol.fbWidth, protocol.fbHeight)
                }
            })

            ctrlAltDelBtn.addActionListener(object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    val protocol = protocol ?: return

                    protocol.sendMessage(KeyEventMessage(Keymap.K_CTRL_LEFT, true))
                    protocol.sendMessage(KeyEventMessage(Keymap.K_ALT_LEFT, true))
                    protocol.sendMessage(KeyEventMessage(Keymap.K_DELETE, true))
                    protocol.sendMessage(KeyEventMessage(Keymap.K_DELETE, false))
                    protocol.sendMessage(KeyEventMessage(Keymap.K_ALT_LEFT, false))
                    protocol.sendMessage(KeyEventMessage(Keymap.K_CTRL_LEFT, false))
                }
            })

        }

        override fun eventDispatched(event: AWTEvent) {
            if (event is MouseEvent) {
                if (event.id != MouseEvent.MOUSE_MOVED) {
                    return
                }

                val c = event.component ?: return

                if (layeredPane.isShowing.not()) return

                val collapse = SwingUtilities.isDescendingFrom(c, toolbar).not()

                if (this.collapse != collapse) {
                    this.collapse = collapse
                    revalidateBounds()
                }
            }
        }

        fun revalidateBounds() {
            val h = max(height, preferredSize.height)
            val w = max(width, preferredSize.width)
            setBounds((layeredPane.width - w) / 2, 0, w, if (collapse) 4 else h)
            revalidate()
            repaint()
        }

        override fun paintChildren(g: Graphics?) {
            if (collapse) {
                return
            }
            super.paintChildren(g)
        }
    }

    private class LayeredPane : JLayeredPane() {
        override fun doLayout() {
            synchronized(treeLock) {
                for (c in components) {
                    if (c is MyToolbar) {
                        c.revalidateBounds()
                    } else {
                        c.setBounds(0, 0, width, height)
                    }
                }
            }
        }
    }
}
