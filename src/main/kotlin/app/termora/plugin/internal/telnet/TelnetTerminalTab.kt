package app.termora.plugin.internal.telnet

import app.termora.Host
import app.termora.ProxyType
import app.termora.PtyHostTerminalTab
import app.termora.WindowScope
import app.termora.terminal.ControlCharacters
import app.termora.terminal.KeyEncoderImpl
import app.termora.terminal.PtyConnector
import app.termora.terminal.TerminalKeyEvent
import org.apache.commons.net.telnet.*
import java.awt.event.KeyEvent
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.charset.Charset


class TelnetTerminalTab(
    windowScope: WindowScope, host: Host,
) : PtyHostTerminalTab(windowScope, host) {
    override suspend fun openPtyConnector(): PtyConnector {
        val winSize = terminalPanel.winSize()
        val telnet = TelnetClient()
        telnet.charset = Charset.forName(host.options.encoding)
        telnet.connectTimeout = 60 * 1000

        if (host.proxy.type == ProxyType.HTTP) {
            telnet.proxy = Proxy(
                Proxy.Type.HTTP,
                InetSocketAddress(host.proxy.host, host.proxy.port)
            )
        } else if (host.proxy.type == ProxyType.SOCKS5) {
            telnet.proxy = Proxy(
                Proxy.Type.SOCKS,
                InetSocketAddress(host.proxy.host, host.proxy.port)
            )
        }

        val characterMode = host.options.extras["character-at-a-time"]?.toBooleanStrictOrNull() ?: false

        val termtype = host.options.envs()["TERM"] ?: "xterm-256color"
        val ttopt = TerminalTypeOptionHandler(termtype, false, false, true, false)
        val echoopt = EchoOptionHandler(false, true, false, true)
        val gaopt = SuppressGAOptionHandler(true, true, true, true)
        val wsopt = WindowSizeOptionHandler(winSize.cols, winSize.rows, true, false, true, false)
        val bopt = SimpleOptionHandler(TelnetOption.BINARY, true, false, true, false)
        val fcopt = SimpleOptionHandler(TelnetOption.REMOTE_FLOW_CONTROL, true, true, false, false)

        telnet.addOptionHandler(bopt)
        telnet.addOptionHandler(fcopt)
        telnet.addOptionHandler(ttopt)
        telnet.addOptionHandler(echoopt)
        telnet.addOptionHandler(gaopt)
        telnet.addOptionHandler(wsopt)

        telnet.connect(host.host, host.port)
        telnet.keepAlive = true
        telnet.tcpNoDelay = characterMode

        val encoder = terminal.getKeyEncoder()
        if (encoder is KeyEncoderImpl) {
            val backspace = host.options.extras["backspace"]
            if (backspace == TelnetHostOptionsPane.Backspace.Backspace.name) {
                encoder.putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_BACK_SPACE), String(byteArrayOf(0x08)))
            } else if (backspace == TelnetHostOptionsPane.Backspace.VT220.name) {
                encoder.putCode(TerminalKeyEvent(keyCode = KeyEvent.VK_BACK_SPACE), "${ControlCharacters.ESC}[3~")
            }
        }


        return ptyConnectorFactory.decorate(TelnetStreamPtyConnector(telnet, telnet.charset, characterMode))
    }


}