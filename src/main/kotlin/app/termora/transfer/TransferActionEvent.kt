package app.termora.transfer

import app.termora.Host
import app.termora.actions.AnActionEvent
import org.apache.commons.lang3.StringUtils
import java.util.*

class TransferActionEvent(
    source: Any,
    val host: Host? = null,
    event: EventObject
) : AnActionEvent(source, StringUtils.EMPTY, event)