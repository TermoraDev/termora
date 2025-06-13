package app.termora.account

import app.termora.*
import app.termora.OptionsPane.Companion.FORM_MARGIN
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.database.DatabaseManager
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.time.DateFormatUtils
import org.jdesktop.swingx.JXHyperlink
import java.awt.BorderLayout
import java.net.URI
import java.util.*
import javax.swing.*
import kotlin.time.Duration.Companion.milliseconds


class AccountOption : JPanel(BorderLayout()), OptionsPane.Option, Disposable {

    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val databaseManager get() = DatabaseManager.getInstance()
    private val accountManager get() = AccountManager.getInstance()
    private val accountProperties get() = AccountProperties.getInstance()
    private val userInfoPanel = JPanel(BorderLayout())
    private val lastSynchronizationOnLabel = JLabel()

    init {
        initView()
        initEvents()
    }


    private fun initView() {
        refreshUserInfoPanel()
        add(userInfoPanel, BorderLayout.CENTER)
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
        val validTo = if (isFreePlan) "-" else if (subscription.endAt >= Long.MAX_VALUE)
            I18n.getString("termora.settings.account.lifetime") else
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
        planBox.add(JLabel(if (isLocally) "-" else subscription.plan.name))
        if (isFreePlan && isLocally.not()) {
            planBox.add(Box.createHorizontalStrut(16))
            val upgrade = JXHyperlink(object : AnAction(I18n.getString("termora.settings.account.upgrade")) {
                override fun actionPerformed(evt: AnActionEvent) {

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
                            Application.browse(URI.create("https://www.termora.app"))
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
                        PullService.getInstance().trigger()
                        PushService.getInstance().trigger()
                        swingCoroutineScope.launch(Dispatchers.IO) {
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

    private fun onLogin() {
        val dialog = LoginServerDialog(owner)
        dialog.isVisible = true
    }

    private fun refreshUserInfoPanel() {
        userInfoPanel.removeAll()
        userInfoPanel.add(getCenterComponent(), BorderLayout.CENTER)
        userInfoPanel.revalidate()
        userInfoPanel.repaint()
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
