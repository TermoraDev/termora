package app.termora.highlight

import app.termora.*
import app.termora.account.AccountManager
import app.termora.account.AccountOwner
import app.termora.database.OwnerType
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import java.awt.Dimension
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JTabbedPane
import javax.swing.UIManager

@Suppress("DuplicatedCode")
class KeywordHighlightDialog(owner: Window) : DialogWrapper(owner) {


    init {
        size = Dimension(UIManager.getInt("Dialog.width"), UIManager.getInt("Dialog.height"))
        isModal = true
        title = I18n.getString("termora.highlight")

        init()
        setLocationRelativeTo(null)
    }

    override fun createCenterPanel(): JComponent {
        val accountManager = AccountManager.getInstance()
        val tabbed = FlatTabbedPane()

        tabbed.styleMap = mapOf(
            "focusColor" to DynamicColor("TabbedPane.background"),
            "hoverColor" to DynamicColor("TabbedPane.background"),
        )
        tabbed.isHasFullBorder = false
        tabbed.border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor)
        tabbed.tabPlacement = JTabbedPane.TOP


        tabbed.addTab(
            I18n.getString("termora.highlight.my-keyword"),
            Icons.user,
            KeywordHighlightPanel(
                AccountOwner(
                    accountManager.getAccountId(),
                    accountManager.getEmail(),
                    OwnerType.User
                )
            ).apply { Disposer.register(disposable, this) }
        )

        if (accountManager.hasTeamFeature()) {
            for (team in accountManager.getTeams()) {
                tabbed.addTab(
                    team.name,
                    Icons.cwmUsers,
                    KeywordHighlightPanel(
                        AccountOwner(
                            team.id,
                            team.name,
                            OwnerType.Team
                        )
                    ).apply { Disposer.register(disposable, this) })
            }
        }


        return tabbed
    }

    override fun createSouthPanel(): JComponent? {
        return null
    }

}