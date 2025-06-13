package app.termora.plugins.obs

import app.termora.Host
import app.termora.protocol.ProtocolHostPanel
import org.apache.commons.lang3.StringUtils

class OBSProtocolHostPanel : ProtocolHostPanel() {
    override fun getHost(): Host {
        return Host(
            name = StringUtils.EMPTY,
            protocol = OBSProtocolProvider.PROTOCOL
        )
    }

    override fun setHost(host: Host) {

    }

    override fun validateFields(): Boolean {
        return true
    }
}