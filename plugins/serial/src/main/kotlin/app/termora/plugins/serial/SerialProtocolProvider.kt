package app.termora.plugins.serial

import app.termora.*
import app.termora.actions.DataProvider
import app.termora.protocol.GenericProtocolProvider
import app.termora.protocol.ProtocolTester

internal class SerialProtocolProvider private constructor() : GenericProtocolProvider, ProtocolTester {
    companion object {
        val instance by lazy { SerialProtocolProvider() }
        const val PROTOCOL = "Serial"
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon {
        return Icons.serial
    }

    override fun createTerminalTab(dataProvider: DataProvider, windowScope: WindowScope, host: Host): TerminalTab {
        return SerialTerminalTab(windowScope, host)
    }

}