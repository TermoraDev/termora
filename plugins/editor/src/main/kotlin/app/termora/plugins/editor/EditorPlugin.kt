package app.termora.plugins.editor

import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.Plugin
import app.termora.sftp.SFTPEditFileExtension

class EditorPlugin : Plugin {
    private val support = ExtensionSupport()

    init {
        support.addExtension(SFTPEditFileExtension::class.java) { MySFTPEditFileExtension.instance }
    }

    override fun getAuthor(): String {
        return "TermoraDev"
    }


    override fun getName(): String {
        return "SFTP File Editor"
    }

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}