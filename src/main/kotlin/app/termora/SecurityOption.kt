package app.termora

import app.termora.database.DatabaseSecret
import java.awt.BorderLayout
import java.awt.GridLayout
import java.security.MessageDigest
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPasswordField

internal class SecurityOption : JPanel(BorderLayout()), OptionsPane.Option {
    private val secret = DatabaseSecret.getInstance()
    private val status = JLabel()
    private val enable = JButton(I18n.getString("termora.settings.security.enable"))
    private val change = JButton(I18n.getString("termora.settings.security.change"))
    private val disable = JButton(I18n.getString("termora.settings.security.disable"))

    init {
        border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
        val content = JPanel(BorderLayout(0, 12))
        content.add(status, BorderLayout.NORTH)
        content.add(JLabel(I18n.getString("termora.settings.security.lost-warning")), BorderLayout.CENTER)
        val buttons = JPanel(GridLayout(0, 1, 0, 8))
        buttons.add(enable)
        buttons.add(change)
        buttons.add(disable)
        content.add(buttons, BorderLayout.SOUTH)
        add(content, BorderLayout.NORTH)
        enable.addActionListener { enableProtection() }
        change.addActionListener { changePassword() }
        disable.addActionListener { disableProtection() }
        refresh()
    }

    override fun getIcon(isSelected: Boolean) = if (secret.isProtectionEnabled()) Icons.locked else Icons.unlocked
    override fun getTitle(): String = I18n.getString("termora.settings.security")
    override fun getJComponent(): JComponent = this

    private fun enableProtection() {
        val password = requestPassword(I18n.getString("termora.settings.security.set-password")) ?: return
        val confirmation = requestPassword(I18n.getString("termora.settings.security.confirm-password"))
        try {
            if (password.size < 1) {
                showError(I18n.getString("termora.settings.security.too-short"))
            } else if (confirmation == null || !constantTimeEquals(password, confirmation)) {
                showError(I18n.getString("termora.settings.security.password-mismatch"))
            } else {
                secret.enable(password)
                refresh()
            }
        } catch (e: Exception) {
            showError(I18n.getString("termora.settings.security.enable-failed"))
        } finally {
            password.fill('\u0000')
            confirmation?.fill('\u0000')
        }
    }

    private fun changePassword() {
        val old = requestPassword(I18n.getString("termora.settings.security.current-password")) ?: return
        val password = requestPassword(I18n.getString("termora.settings.security.new-password"))
        val confirmation = requestPassword(I18n.getString("termora.settings.security.confirm-new-password"))
        try {
            if (password == null || password.size < 1 || confirmation == null || !constantTimeEquals(password, confirmation)) {
                showError(I18n.getString("termora.settings.security.new-password-mismatch"))
            } else {
                secret.change(old, password)
                refresh()
            }
        } catch (_: Exception) {
            showError(I18n.getString("termora.settings.security.incorrect-password"))
        } finally {
            old.fill('\u0000')
            password?.fill('\u0000')
            confirmation?.fill('\u0000')
        }
    }

    private fun disableProtection() {
        if (JOptionPane.showConfirmDialog(this, I18n.getString("termora.settings.security.disable-warning"), I18n.getString("termora.settings.security.disable-title"), JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return
        val password = requestPassword(I18n.getString("termora.settings.security.current-password")) ?: return
        try {
            secret.disable(password)
            refresh()
        } catch (_: Exception) {
            showError(I18n.getString("termora.settings.security.incorrect-password"))
        } finally {
            password.fill('\u0000')
        }
    }

    private fun requestPassword(title: String): CharArray? {
        val field = JPasswordField()
        return if (JOptionPane.showConfirmDialog(this, field, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) field.password else null
    }

    private fun refresh() {
        val protected = secret.isProtectionEnabled()
        status.text = if (protected) I18n.getString("termora.settings.security.enabled-status") else I18n.getString("termora.settings.security.disabled-status")
        enable.isEnabled = !protected
        change.isEnabled = protected
        disable.isEnabled = protected
    }

    private fun showError(message: String) {
        JOptionPane.showMessageDialog(this, message, I18n.getString("termora.settings.security"), JOptionPane.ERROR_MESSAGE)
    }

    private fun constantTimeEquals(first: CharArray, second: CharArray): Boolean {
        var difference = first.size xor second.size
        for (index in 0 until maxOf(first.size, second.size)) {
            val a = if (index < first.size) first[index].code else 0
            val b = if (index < second.size) second[index].code else 0
            difference = difference or (a xor b)
        }
        return difference == 0
    }
}
