package app.termora.keyboardinteractive

import org.apache.commons.lang3.StringUtils
import org.apache.sshd.client.auth.keyboard.UserInteraction
import org.apache.sshd.client.session.ClientSession
import java.awt.Window
import javax.swing.SwingUtilities

class TerminalUserInteraction(
    private val owner: Window
) : UserInteraction {


    override fun interactive(
        session: ClientSession?,
        name: String?,
        instruction: String?,
        lang: String?,
        prompt: Array<out String>,
        echo: BooleanArray
    ): Array<String> {
        val passwords = Array(prompt.size) { StringUtils.EMPTY }

        SwingUtilities.invokeAndWait {
            for (i in prompt.indices) {
                val dialog = KeyboardInteractiveDialog(
                    owner,
                    prompt[i],
                    true
                )
                dialog.title = instruction ?: name ?: StringUtils.EMPTY
                passwords[i] = dialog.getText()
                if (passwords[i].isBlank()) {
                    break
                }
            }
        }

        if (passwords.last().isBlank()) {
            throw IllegalStateException("User interaction was cancelled.")
        }

        if (passwords.all { it.isEmpty() }) {
            return emptyArray()
        }

        return passwords
    }

    override fun getUpdatedPassword(session: ClientSession?, prompt: String?, lang: String?): String {
        throw UnsupportedOperationException()
    }
}