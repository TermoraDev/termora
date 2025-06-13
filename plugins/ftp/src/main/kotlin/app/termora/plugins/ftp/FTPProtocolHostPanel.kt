package app.termora.plugins.ftp

import app.termora.Host
import app.termora.protocol.ProtocolHostPanel
import org.apache.commons.lang3.StringUtils

class FTPProtocolHostPanel : ProtocolHostPanel() {
    override fun getHost(): Host {
        return Host(
            name = StringUtils.EMPTY,
            protocol = FTPProtocolProvider.PROTOCOL
        )
    }

    override fun setHost(host: Host) {

    }

    override fun validateFields(): Boolean {
        return true
    }
}