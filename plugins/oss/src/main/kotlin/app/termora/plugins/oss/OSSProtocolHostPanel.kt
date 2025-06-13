package app.termora.plugins.oss

import app.termora.Host
import app.termora.protocol.ProtocolHostPanel
import org.apache.commons.lang3.StringUtils

class OSSProtocolHostPanel : ProtocolHostPanel() {
    override fun getHost(): Host {
        return Host(
            name = StringUtils.EMPTY,
            protocol = OSSProtocolProvider.PROTOCOL
        )
    }

    override fun setHost(host: Host) {

    }

    override fun validateFields(): Boolean {
        return true
    }
}