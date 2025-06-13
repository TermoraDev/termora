package app.termora.tag

import app.termora.*
import app.termora.account.AccountManager
import app.termora.account.AccountOwner
import app.termora.database.OwnerType
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import org.apache.commons.lang3.StringUtils
import java.awt.Dimension
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JTabbedPane
import javax.swing.UIManager

@Suppress("DuplicatedCode")
class TagDialog(owner: Window, private val accountOwnerId: String = StringUtils.EMPTY) : DialogWrapper(owner) {


    init {
        size = Dimension(UIManager.getInt("Dialog.width"), UIManager.getInt("Dialog.height"))
        isModal = true
        title = I18n.getString("termora.tag")

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
            I18n.getString("termora.tag.my-tags"),
            Icons.user,
            TagPanel(
                AccountOwner(
                    accountManager.getAccountId(),
                    accountManager.getEmail(),
                    OwnerType.User
                )
            ).apply { Disposer.register(disposable, this) }
        )

        if (accountManager.isSigned()) {
            for (team in accountManager.getTeams()) {
                tabbed.addTab(
                    team.name,
                    Icons.cwmUsers,
                    TagPanel(
                        AccountOwner(
                            team.id,
                            team.name,
                            OwnerType.Team
                        )
                    ).apply { Disposer.register(disposable, this) })

                if (accountOwnerId == team.id) {
                    tabbed.selectedIndex = tabbed.tabCount - 1
                }
            }
        }

        return tabbed
    }

    override fun createSouthPanel(): JComponent? {
        return null
    }

}