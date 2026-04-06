package app.termora.account

import app.termora.*
import app.termora.Application.ohMyJson
import app.termora.OptionsPane.Companion.FORM_MARGIN
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.database.DatabaseManager
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.apache.commons.codec.binary.Hex
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.time.DateFormatUtils
import org.jdesktop.swingx.JXBusyLabel
import org.jdesktop.swingx.JXHyperlink
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.CardLayout
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.*
import kotlin.time.Duration.Companion.milliseconds


class AccountOption : JPanel(BorderLayout()), OptionsPane.Option, Disposable {
    companion object {
        private val log = LoggerFactory.getLogger(AccountOption::class.java)
    }

    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val databaseManager get() = DatabaseManager.getInstance()
    private val accountManager get() = AccountManager.getInstance()
    private val accountProperties get() = AccountProperties.getInstance()
    private val userInfoPanel = JPanel(BorderLayout())
    private val lastSynchronizationOnLabel = JLabel()
    private val serverManager get() = ServerManager.getInstance()
    private val cardLayout = CardLayout()
    private val contentPanel = JPanel(cardLayout)
    private val loginPanel = JPanel(BorderLayout())
    private val busyLabel = JXBusyLabel()
    private var httpServer: HttpServer? = null

    init {
        initView()
        initEvents()
    }

    private fun initView() {
        refreshUserInfoPanel()
        refreshLoginPanel()

        contentPanel.add(userInfoPanel, "UserInfo")
        contentPanel.add(loginPanel, "Login")

        cardLayout.show(contentPanel, "UserInfo")

        add(contentPanel, BorderLayout.CENTER)


    }

    private fun initEvents() {
        // 服务器签名发生变更
        DynamicExtensionHandler.getInstance()
            .register(ServerSignedExtension::class.java, object : ServerSignedExtension {
                override fun onSignedChanged(oldSigned: Boolean, newSigned: Boolean) {
                    refreshUserInfoPanel()
                }
            }).let { Disposer.register(this, it) }

        // 账号发生变化
        DynamicExtensionHandler.getInstance()
            .register(AccountExtension::class.java, object : AccountExtension {
                override fun onAccountChanged(oldAccount: Account, newAccount: Account) {
                    if (oldAccount.id != newAccount.id) {
                        refreshUserInfoPanel()
                    }
                }
            }).let { Disposer.register(this, it) }
    }

    private fun getCenterComponent(): JComponent {
        val layout = FormLayout(
            "left:pref, $FORM_MARGIN, default:grow",
            "pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref"
        )

        var rows = 1
        val step = 2

        val subscription = accountManager.getSubscription()
        val isFreePlan = accountManager.isFreePlan()
        val isLocally = accountManager.isLocally()
        val validTo = if (isFreePlan) "-"
        else if (subscription.endAt >= Long.MAX_VALUE)
            I18n.getString("termora.settings.account.lifetime")
        else
            DateFormatUtils.format(Date(subscription.endAt), I18n.getString("termora.date-format"))
        val lastSynchronizationOn = if (isFreePlan) "-" else
            DateFormatUtils.format(
                Date(accountManager.getLastSynchronizationOn()),
                I18n.getString("termora.date-format")
            )

        var server = accountManager.getServer()
        var email = accountManager.getEmail()
        if (isLocally) {
            server = I18n.getString("termora.settings.account.locally")
            email = I18n.getString("termora.settings.account.locally")
        }

        val planBox = Box.createHorizontalBox()
        planBox.add(JLabel(if (isLocally) "-" else subscription.plan))
        if (isFreePlan && isLocally.not()) {
            planBox.add(Box.createHorizontalStrut(16))
            val upgrade = JXHyperlink(object : AnAction(I18n.getString("termora.settings.account.upgrade")) {
                override fun actionPerformed(evt: AnActionEvent) {
                    Application.browse(URI.create("${accountManager.getServer()}/v1/client/redirect?to=upgrade&version=${Application.getVersion()}"))
                }
            })
            upgrade.isFocusable = false
            planBox.add(upgrade)
        }

        val serverBox = Box.createHorizontalBox()
        serverBox.add(JLabel(server))
        if (isLocally.not()) {
            serverBox.add(Box.createHorizontalStrut(8))
            if (accountManager.isSigned().not()) {
                val upgrade =
                    JXHyperlink(object : AnAction(I18n.getString("termora.settings.account.verify"), Icons.error) {
                        override fun actionPerformed(evt: AnActionEvent) {
                            Application.browse(URI.create("https://www.termora.app?version=${Application.getVersion()}"))
                        }
                    })
                upgrade.isFocusable = false
                serverBox.add(upgrade)
            } else {
                serverBox.add(JLabel(Icons.success))
            }
        }

        lastSynchronizationOnLabel.text = lastSynchronizationOn

        return FormBuilder.create().layout(layout).debug(false)
            .add("${I18n.getString("termora.settings.account.server")}:").xy(1, rows)
            .add(serverBox).xy(3, rows).apply { rows += step }
            .add("${I18n.getString("termora.settings.account")}:").xy(1, rows)
            .add(email).xy(3, rows).apply { rows += step }
            .add("${I18n.getString("termora.settings.account.subscription")}:").xy(1, rows)
            .add(planBox).xy(3, rows).apply { rows += step }
            .add("${I18n.getString("termora.settings.account.valid-to")}:").xy(1, rows)
            .add(validTo).xy(3, rows).apply { rows += step }
            .add("${I18n.getString("termora.settings.account.synchronization-on")}:").xy(1, rows)
            .add(lastSynchronizationOnLabel).xy(3, rows).apply { rows += step }
            .add(createActionPanel(isFreePlan)).xyw(1, rows, 3).apply { rows += step }
            .build()
    }

    private fun getLoginComponent(): JComponent {
        val layout = FormLayout(
            "default:grow",
            "pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref"
        )

        val cancelBtn = JXHyperlink(object : AnAction(I18n.getString("termora.cancel")) {
            override fun actionPerformed(evt: AnActionEvent) {
                httpServer?.stop(0)
                cardLayout.show(contentPanel, "UserInfo")
            }
        })

        val tipLabel = JLabel(I18n.getString("termora.settings.account.wait-login"))
        tipLabel.foreground = UIManager.getColor("TextField.placeholderForeground")

        return FormBuilder.create().layout(layout).debug(false).padding("10dlu,0,0,0")
            .add(busyLabel).xy(1, 1, "center, fill")
            .add(tipLabel).xy(1, 3, "center, fill")
            .add(cancelBtn).xy(1, 5, "center, fill")
            .build()
    }

    private fun createActionPanel(isFreePlan: Boolean): JComponent {
        val actionBox = Box.createHorizontalBox()
        actionBox.add(Box.createHorizontalGlue())
        val actions = mutableSetOf<JComponent>()

        if (accountManager.isLocally()) {
            actions.add(JXHyperlink(object : AnAction("${I18n.getString("termora.settings.account.login")}...") {
                override fun actionPerformed(evt: AnActionEvent) {
                    onLogin()
                }
            }).apply { isFocusable = false })
        } else {
            if (isFreePlan.not()) {
                actions.add(JXHyperlink(object : AnAction(I18n.getString("termora.settings.account.sync-now")) {
                    override fun actionPerformed(evt: AnActionEvent) {
                        // 全量同步
                        accountProperties.nextSynchronizationSince = 0

                        // 拉取
                        PullService.getInstance().trigger()

                        // 推送
                        PushService.getInstance().trigger()

                        swingCoroutineScope.launch(Dispatchers.IO) {
                            // 刷新账户
                            accountManager.refreshAccount()

                            withContext(Dispatchers.Swing) {
                                isEnabled = false
                                lastSynchronizationOnLabel.text = DateFormatUtils.format(
                                    Date(System.currentTimeMillis()),
                                    I18n.getString("termora.date-format")
                                )
                            }
                            delay(1500.milliseconds)
                            withContext(Dispatchers.Swing) { isEnabled = true }
                        }
                    }
                }).apply { isFocusable = false })
            }

            actions.add(JXHyperlink(object : AnAction(I18n.getString("termora.settings.account.logout")) {
                override fun actionPerformed(evt: AnActionEvent) {
                    val hasUnsyncedData = databaseManager.unsyncedData().isNotEmpty()
                    val message = if (hasUnsyncedData) "termora.settings.account.unsynced-logout-confirm"
                    else "termora.settings.account.logout-confirm"
                    val option = OptionPane.showConfirmDialog(
                        owner, I18n.getString(message),
                        optionType = JOptionPane.OK_CANCEL_OPTION,
                        messageType = JOptionPane.QUESTION_MESSAGE,
                    )
                    if (option != JOptionPane.OK_OPTION) {
                        return
                    }
                    AccountManager.getInstance().logout()
                }
            }).apply { isFocusable = false })
        }

        for (component in actions) {
            actionBox.add(component)
            if (actions.last() != component) {
                actionBox.add(Box.createHorizontalStrut(8))
            }
        }

        actionBox.add(Box.createHorizontalGlue())



        return actionBox
    }

    private fun showLoginPanel() {
        refreshLoginPanel()
        busyLabel.isBusy = true
        cardLayout.show(contentPanel, "Login")
    }

    private fun onLogin() {
        httpServer?.stop(0)

        val dialog = LoginServerDialog(owner)
        dialog.isVisible = true
        val server = dialog.server ?: return

        showLoginPanel()

        onLogin(server)
    }


    private fun onLogin(server: Server) {

        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            .apply { httpServer = this }
        val future = processLogin(server, httpServer)

        val loginJob = swingCoroutineScope.launch(Dispatchers.IO) {
            try {
                val loginResult = future.get(5, TimeUnit.MINUTES)
                serverManager.login(server, loginResult.refreshToken, loginResult.password)
            } catch (e: Exception) {
                if (log.isErrorEnabled) log.error(e.message, e)
                withContext(Dispatchers.Swing) {
                    OptionPane.showMessageDialog(
                        owner,
                        StringUtils.defaultIfBlank(
                            e.message ?: StringUtils.EMPTY,
                            I18n.getString("termora.settings.account.login-failed")
                        ),
                        messageType = JOptionPane.ERROR_MESSAGE,
                    )
                }
            } finally {
                withContext(Dispatchers.Swing) { cardLayout.show(contentPanel, "UserInfo") }
                httpServer.stop(0)
            }
        }

        Disposer.register(this, object : Disposable {
            override fun dispose() {
                loginJob.cancel()
                httpServer.stop(0)
            }
        })
    }

    override fun dispose() {
        busyLabel.isBusy = false
        super.dispose()
    }

    private fun processLogin(server: Server, httpServer: HttpServer): CompletableFuture<LoginResult> {
        val keypair = RSA.generateKeyPair(2048)
        val future = CompletableFuture<LoginResult>()

        httpServer.createContext("/callback") { exchange ->
            val method = exchange.requestMethod
            if (method.equals("OPTIONS", ignoreCase = true)) {
                exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
                exchange.responseHeaders.add("Access-Control-Allow-Methods", "POST, OPTIONS")
                exchange.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type")
                exchange.sendResponseHeaders(204, -1)
            } else {
                var loginResult: LoginResult? = null

                if (method.equals("POST", ignoreCase = true)) {
                    try {
                        val text = String(exchange.requestBody.readAllBytes())
                        loginResult = ohMyJson.decodeFromString<LoginResult>(text)

                        val secretKey = RSA.decrypt(keypair.private, Hex.decodeHex(loginResult.secretKey))
                        val secretIv = RSA.decrypt(keypair.private, Hex.decodeHex(loginResult.secretIv))
                        val password = AES.CBC.decrypt(secretKey, secretIv, Hex.decodeHex(loginResult.password))
                        val refreshToken = AES.CBC.decrypt(
                            secretKey, secretIv, Hex.decodeHex(loginResult.refreshToken)
                        )
                        loginResult = loginResult.copy(
                            password = String(password),
                            refreshToken = String(refreshToken)
                        )
                    } catch (e: Exception) {
                        if (log.isErrorEnabled) {
                            log.error(e.message, e)
                        }
                    }
                }

                val response = "OK".toByteArray()
                exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }

                if (loginResult != null) {
                    future.complete(loginResult)
                }
            }
            IOUtils.closeQuietly { exchange.close() }
        }
        httpServer.start()

        val sb = StringBuilder()
        val redirect = StringBuilder()
        redirect.append("/device?callback=").append("http://127.0.0.1:${httpServer.address.port}/callback")
        redirect.append("&from=device&publicKey=").append(keypair.public.encoded.toHexString())
        redirect.append("&format=hex&device=termora&device-version=").append(Application.getVersion())

        sb.append(server.server)
        sb.append("/v1/client/redirect?to=login&from=device")
        sb.append("&redirect=").append(URLEncoder.encode(redirect.toString(), Charsets.UTF_8))

        Application.browse(URI.create(sb.toString()))

        return future
    }

    @Serializable
    private data class LoginResult(
        val password: String,
        val refreshToken: String,
        val secretKey: String,
        val secretIv: String,
    )


    private fun refreshUserInfoPanel() {
        userInfoPanel.removeAll()
        userInfoPanel.add(getCenterComponent(), BorderLayout.CENTER)
        userInfoPanel.revalidate()
        userInfoPanel.repaint()
    }

    private fun refreshLoginPanel() {
        loginPanel.removeAll()
        loginPanel.add(getLoginComponent(), BorderLayout.CENTER)
        loginPanel.revalidate()
        loginPanel.repaint()
    }

    override fun getIcon(isSelected: Boolean): Icon {
        return Icons.user
    }

    override fun getTitle(): String {
        return I18n.getString("termora.settings.account")
    }

    override fun getJComponent(): JComponent {
        return this
    }

    override fun getAnchor(): OptionsPane.Anchor {
        return OptionsPane.Anchor.First
    }

    override fun getIdentifier(): String {
        return "Account"
    }

}
