package app.termora.plugin.internal.ssh

import app.termora.*
import app.termora.OptionsPane.Companion.FORM_MARGIN
import app.termora.account.AccountOwner
import app.termora.highlight.KeywordHighlight
import app.termora.keymgr.KeyManager
import app.termora.keymgr.KeyManagerDialog
import app.termora.plugin.internal.AltKeyModifier
import app.termora.plugin.internal.BasicProxyOption
import app.termora.plugin.internal.BasicTerminalOption
import app.termora.plugin.internal.telnet.TelnetHostOptionsPane.Backspace
import app.termora.tree.Filter
import app.termora.tree.HostTreeNode
import app.termora.tree.NewHostTreeDialog
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatComboBox
import com.formdev.flatlaf.ui.FlatTextBorder
import com.formdev.flatlaf.util.SystemInfo
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.lang3.StringUtils
import org.eclipse.jgit.internal.transport.sshd.agent.connector.PageantConnector
import org.eclipse.jgit.internal.transport.sshd.agent.connector.UnixDomainSocketConnector
import org.eclipse.jgit.internal.transport.sshd.agent.connector.WinPipeConnector
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

internal class SSHSFTPOption : JPanel(BorderLayout()), OptionsPane.Option {
    val defaultDirectoryField = OutlineTextField(255)


    init {
        initView()
        initEvents()
    }

    private fun initView() {
        add(getCenterComponent(), BorderLayout.CENTER)
    }

    private fun initEvents() {

    }


    override fun getIcon(isSelected: Boolean): Icon {
        return Icons.folder
    }

    override fun getTitle(): String {
        return I18n.getString("termora.transport.sftp")
    }

    override fun getJComponent(): JComponent {
        return this
    }

    private fun getCenterComponent(): JComponent {
        val layout = FormLayout(
            "left:pref, $FORM_MARGIN, default:grow",
            "pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref"
        )

        var rows = 1
        val step = 2
        val panel = FormBuilder.create().layout(layout)
            .add("${I18n.getString("termora.settings.sftp.default-directory")}:").xy(1, rows)
            .add(defaultDirectoryField).xy(3, rows).apply { rows += step }
            .build()


        return panel
    }
}

