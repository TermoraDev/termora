package app.termora.plugins.s3

import app.termora.protocol.ProtocolHostPanel
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProvider

class S3ProtocolHostPanelExtension private constructor() : ProtocolHostPanelExtension {
    companion object {
        val instance by lazy { S3ProtocolHostPanelExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return S3ProtocolProvider.instance
    }

    override fun createProtocolHostPanel(): ProtocolHostPanel {
        return S3ProtocolHostPanel()
    }
}