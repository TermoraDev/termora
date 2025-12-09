package app.termora

import app.termora.actions.AnActionEvent
import java.util.*

class OpenHostActionEvent(source: Any, val host: Host, event: EventObject, val tabIndex: Int = -1) :
    AnActionEvent(source, String(), event)