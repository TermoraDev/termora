package app.termora.account

import app.termora.plugin.Extension

interface ServerSignedExtension : Extension {
    /**
     * 签名发生变化
     */
    fun onSignedChanged(oldSigned: Boolean, newSigned: Boolean)
}