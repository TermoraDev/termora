package app.termora.plugin.internal.ssh

import app.termora.Disposer
import app.termora.Host
import app.termora.protocol.ProtocolHostPanel
import java.awt.BorderLayout

class SSHProtocolHostPanel : ProtocolHostPanel() {

    private val pane = SSHHostOptionsPane()

    init {
        initView()
        initEvents()
    }


    private fun initView() {
        add(pane, BorderLayout.CENTER)
        Disposer.register(this, pane)
    }

    private fun initEvents() {}


    override fun getHost(): Host {
        return pane.getHost()
    }

    override fun setHost(host: Host) {
        pane.setHost(host)
    }

    override fun validateFields(): Boolean {
        return pane.validateFields()
    }
}