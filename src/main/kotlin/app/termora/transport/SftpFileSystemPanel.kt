package app.termora.transport

import app.termora.*
import app.termora.keyboardinteractive.TerminalUserInteraction
import com.formdev.flatlaf.icons.FlatOptionPaneErrorIcon
import com.formdev.flatlaf.icons.FlatOptionPaneInformationIcon
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.sftp.client.SftpClientFactory
import org.apache.sshd.sftp.client.fs.SftpFileSystem
import org.jdesktop.swingx.JXBusyLabel
import org.jdesktop.swingx.JXHyperlink
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.ActionEvent
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*

class SftpFileSystemPanel(
    private val transportManager: TransportManager,
    private var host: Host? = null
) : JPanel(BorderLayout()), Disposable,
    FileSystemTransportListener.Provider {

    companion object {
        private val log = LoggerFactory.getLogger(SftpFileSystemPanel::class.java)

        private enum class State {
            Initialized,
            Connecting,
            Connected,
            ConnectFailed,
        }
    }

    @Volatile
    private var state = State.Initialized
    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)

    private val connectingPanel = ConnectingPanel()
    private val selectHostPanel = SelectHostPanel()
    private val connectFailedPanel = ConnectFailedPanel()
    private val listeners = mutableListOf<FileSystemTransportListener>()
    private val isDisposed = AtomicBoolean(false)

    private var client: SshClient? = null
    private var session: ClientSession? = null
    private var fileSystem: SftpFileSystem? = null
    var fileSystemPanel: FileSystemPanel? = null


    init {
        initView()
        initEvents()
    }

    private fun initView() {
        cardPanel.add(selectHostPanel, State.Initialized.name)
        cardPanel.add(connectingPanel, State.Connecting.name)
        cardPanel.add(connectFailedPanel, State.ConnectFailed.name)
        cardLayout.show(cardPanel, State.Initialized.name)
        add(cardPanel, BorderLayout.CENTER)
    }

    private fun initEvents() {

    }

    @OptIn(DelicateCoroutinesApi::class)
    fun connect() {
        GlobalScope.launch(Dispatchers.IO) {
            if (state != State.Connecting) {
                state = State.Connecting

                withContext(Dispatchers.Swing) {
                    connectingPanel.start()
                    cardLayout.show(cardPanel, State.Connecting.name)
                }

                runCatching { doConnect() }.onFailure {
                    if (log.isErrorEnabled) {
                        log.error(it.message, it)
                    }
                    withContext(Dispatchers.Swing) {
                        state = State.ConnectFailed
                        connectFailedPanel.errorLabel.text = ExceptionUtils.getRootCauseMessage(it)
                        cardLayout.show(cardPanel, State.ConnectFailed.name)
                    }
                }

                withContext(Dispatchers.Swing) {
                    connectingPanel.stop()
                }
            }

        }
    }

    private suspend fun doConnect() {

        val host = this.host ?: return

        closeIO()

        try {
            val client = SshClients.openClient(host).apply { client = this }
            withContext(Dispatchers.Swing) {
                client.userInteraction =
                    TerminalUserInteraction(SwingUtilities.getWindowAncestor(this@SftpFileSystemPanel))
            }
            val session = SshClients.openSession(host, client).apply { session = this }
            fileSystem = SftpClientFactory.instance().createSftpFileSystem(session)
            session.addCloseFutureListener { onClose() }
        } catch (e: Exception) {
            closeIO()
            throw e
        }

        if (isDisposed.get()) {
            throw IllegalStateException("Closed")
        }

        val fileSystem = this.fileSystem ?: return

        withContext(Dispatchers.Swing) {
            state = State.Connected

            val fileSystemPanel = FileSystemPanel(fileSystem, transportManager, host)
            fileSystemPanel.addFileSystemTransportListener(object : FileSystemTransportListener {
                override fun transport(
                    fileSystemPanel: FileSystemPanel,
                    workdir: Path,
                    isDirectory: Boolean,
                    path: Path
                ) {
                    listeners.forEach { it.transport(fileSystemPanel, workdir, isDirectory, path) }
                }
            })

            cardPanel.add(fileSystemPanel, State.Connected.name)
            cardLayout.show(cardPanel, State.Connected.name)

            firePropertyChange("TabName", StringUtils.EMPTY, host.name)

            this@SftpFileSystemPanel.fileSystemPanel = fileSystemPanel

            // 立即加载
            fileSystemPanel.reload()
        }

    }

    private fun onClose() {
        if (isDisposed.get()) {
            return
        }

        SwingUtilities.invokeLater {
            closeIO()
            state = State.ConnectFailed
            connectFailedPanel.errorLabel.text = I18n.getString("termora.transport.sftp.closed")
            cardLayout.show(cardPanel, State.ConnectFailed.name)
        }
    }

    private fun closeIO() {
        val host = host

        fileSystemPanel?.let { Disposer.dispose(it) }
        fileSystemPanel = null

        runCatching { IOUtils.closeQuietly(fileSystem) }
        runCatching { IOUtils.closeQuietly(session) }
        runCatching { IOUtils.closeQuietly(client) }

        if (host != null && log.isInfoEnabled) {
            log.info("Sftp ${host.name} is closed")
        }
    }

    override fun dispose() {
        if (isDisposed.compareAndSet(false, true)) {
            closeIO()
        }
    }

    private class ConnectingPanel : JPanel(BorderLayout()) {
        private val busyLabel = JXBusyLabel()

        init {
            initView()
        }

        private fun initView() {
            val formMargin = "7dlu"
            val layout = FormLayout(
                "default:grow, pref, default:grow",
                "40dlu, pref, $formMargin, pref"
            )

            val label = JLabel(I18n.getString("termora.transport.sftp.connecting"))
            label.horizontalAlignment = SwingConstants.CENTER

            busyLabel.horizontalAlignment = SwingConstants.CENTER
            busyLabel.verticalAlignment = SwingConstants.CENTER

            val builder = FormBuilder.create().layout(layout).debug(false)
            builder.add(busyLabel).xy(2, 2, "fill, center")
            builder.add(label).xy(2, 4)
            add(builder.build(), BorderLayout.CENTER)
        }

        fun start() {
            busyLabel.isBusy = true
        }

        fun stop() {
            busyLabel.isBusy = false
        }
    }

    private inner class ConnectFailedPanel : JPanel(BorderLayout()) {
        val errorLabel = JLabel()

        init {
            initView()
        }

        private fun initView() {
            val formMargin = "4dlu"
            val layout = FormLayout(
                "default:grow, pref, default:grow",
                "40dlu, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
            )

            errorLabel.horizontalAlignment = SwingConstants.CENTER

            val builder = FormBuilder.create().layout(layout).debug(false)
            builder.add(FlatOptionPaneErrorIcon()).xy(2, 2)
            builder.add(errorLabel).xyw(1, 4, 3, "fill, center")
            builder.add(JXHyperlink(object : AbstractAction(I18n.getString("termora.transport.sftp.retry")) {
                override fun actionPerformed(e: ActionEvent) {
                    connect()
                }
            }).apply {
                horizontalAlignment = SwingConstants.CENTER
                verticalAlignment = SwingConstants.CENTER
                isFocusable = false
            }).xy(2, 6)
            builder.add(JXHyperlink(object :
                AbstractAction(I18n.getString("termora.transport.sftp.select-another-host")) {
                override fun actionPerformed(e: ActionEvent) {
                    state = State.Initialized
                    this@SftpFileSystemPanel.firePropertyChange("TabName", StringUtils.SPACE, StringUtils.EMPTY)
                    cardLayout.show(cardPanel, State.Initialized.name)
                }
            }).apply {
                horizontalAlignment = SwingConstants.CENTER
                verticalAlignment = SwingConstants.CENTER
                isFocusable = false
            }).xy(2, 8)
            add(builder.build(), BorderLayout.CENTER)
        }
    }

    private inner class SelectHostPanel : JPanel(BorderLayout()) {
        init {
            initView()
        }

        private fun initView() {
            val formMargin = "4dlu"
            val layout = FormLayout(
                "default:grow, pref, default:grow",
                "40dlu, pref, $formMargin, pref, $formMargin, pref"
            )


            val errorInfo = JLabel(I18n.getString("termora.transport.sftp.connect-a-host"))
            errorInfo.horizontalAlignment = SwingConstants.CENTER

            val builder = FormBuilder.create().layout(layout).debug(false)
            builder.add(FlatOptionPaneInformationIcon()).xy(2, 2)
            builder.add(errorInfo).xyw(1, 4, 3, "fill, center")
            builder.add(JXHyperlink(object : AbstractAction(I18n.getString("termora.transport.sftp.select-host")) {
                override fun actionPerformed(e: ActionEvent) {
                    val dialog = HostTreeDialog(SwingUtilities.getWindowAncestor(this@SftpFileSystemPanel))
                    dialog.allowMulti = false
                    dialog.setLocationRelativeTo(this@SelectHostPanel)
                    dialog.isVisible = true
                    this@SftpFileSystemPanel.host = dialog.hosts.firstOrNull() ?: return
                    connect()
                }
            }).apply {
                horizontalAlignment = SwingConstants.CENTER
                verticalAlignment = SwingConstants.CENTER
                isFocusable = false
            }).xy(2, 6)
            add(builder.build(), BorderLayout.CENTER)
        }
    }


    override fun addFileSystemTransportListener(listener: FileSystemTransportListener) {
        listeners.add(listener)
    }

    override fun removeFileSystemTransportListener(listener: FileSystemTransportListener) {
        listeners.remove(listener)
    }
}