package app.termora.actions

import app.termora.Host
import app.termora.I18n
import app.termora.Icons
import app.termora.OpenHostActionEvent

class OpenLocalTerminalAction : AnAction(
    I18n.getString("termora.find-everywhere.quick-command.local-terminal"),
    Icons.terminal
) {
    companion object {
        const val LOCAL_TERMINAL = "OpenLocalTerminal"
    }


    init {
        putValue(SHORT_DESCRIPTION, I18n.getString("termora.actions.open-local-terminal"))
        putValue(ACTION_COMMAND_KEY, LOCAL_TERMINAL)
    }


    override fun actionPerformed(evt: AnActionEvent) {
        ActionManager.getInstance().getAction(OpenHostAction.OPEN_HOST)?.actionPerformed(
            OpenHostActionEvent(
                evt.source,
                Host(
                    id = "local",
                    name = name,
                    protocol = "Local"
                ),
                evt
            )
        )
        evt.consume()
    }


}