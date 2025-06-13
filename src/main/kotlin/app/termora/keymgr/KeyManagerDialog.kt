package app.termora.keymgr

import app.termora.DialogWrapper
import app.termora.DynamicColor
import app.termora.I18n
import app.termora.Icons
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

class KeyManagerDialog(
    owner: Window,
    private val selectMode: Boolean = false,
    size: Dimension = Dimension(UIManager.getInt("Dialog.width"), UIManager.getInt("Dialog.height")),
) : DialogWrapper(owner) {
    var ok: Boolean = false

    init {
        super.setSize(size.width, size.height)
        isModal = true
        title = I18n.getString("termora.keymgr.title")
        setLocationRelativeTo(null)

        init()

        /*if (selectMode) {
            keyManagerPanel.keyPairTable.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount % 2 == 0 && SwingUtilities.isLeftMouseButton(e)) {
                        if (keyManagerPanel.keyPairTable.selectedRowCount > 0) {
                            SwingUtilities.invokeLater { doOKAction() }
                        }
                    }
                }
            })
        }*/
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
            I18n.getString("termora.settings.sync.range.keys"),
            Icons.user,
            KeyManagerPanel(AccountOwner(accountManager.getAccountId(), accountManager.getEmail(), OwnerType.User))
        )

        if (accountManager.isSigned()) {
            for (team in accountManager.getTeams()) {
                tabbed.addTab(
                    team.name,
                    Icons.cwmUsers,
                    KeyManagerPanel(
                        AccountOwner(
                            team.id,
                            team.name,
                            OwnerType.Team
                        )
                    )
                )
            }
        }


        return tabbed


    }

    override fun createSouthPanel(): JComponent? {
        return if (selectMode) super.createSouthPanel() else null
    }

    override fun doOKAction() {
        /*if (selectMode) {
            if (keyManagerPanel.keyPairTable.selectedRowCount < 1) {
                OptionPane.showMessageDialog(this, "Please select a Key")
                return
            }
        }*/
        ok = true
        super.doOKAction()
    }

    fun getLasOhKeyPair(): OhKeyPair? {
        /*if (keyManagerPanel.keyPairTable.selectedRowCount > 0) {
            val row = keyManagerPanel.keyPairTable.selectedRows.last()
            return keyManagerPanel.keyPairTableModel.getOhKeyPair(row)
        }*/
        return null
    }

}