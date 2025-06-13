package app.termora.plugin.internal.ssh

import app.termora.*
import app.termora.actions.DataProvider
import app.termora.protocol.GenericProtocolProvider
import app.termora.protocol.ProtocolTestRequest
import app.termora.protocol.ProtocolTester
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession

internal class SSHProtocolProvider private constructor() : GenericProtocolProvider, ProtocolTester {
    companion object {
        val instance by lazy { SSHProtocolProvider() }
        const val PROTOCOL = "SSH"
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun createTerminalTab(dataProvider: DataProvider, windowScope: WindowScope, host: Host): TerminalTab {
        return SSHTerminalTab(windowScope, host)
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon {
        return Icons.ssh
    }

    override fun canTestConnection(requester: ProtocolTestRequest): Boolean {
        return true
    }

    override fun testConnection(requester: ProtocolTestRequest) {
        var client: SshClient? = null
        var session: ClientSession? = null
        val owner = requester.owner
        val host = requester.host
        try {
            client = if (owner != null) SshClients.openClient(host, owner) else SshClients.openClient(host)
            session = SshClients.openSession(host, client)
        } finally {
            session?.close()
            client?.close()
        }
    }

    override fun ordered() = Int.MIN_VALUE
}