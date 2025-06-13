package app.termora.plugin.internal.plugin

import app.termora.plugin.Extension
import app.termora.plugin.Plugin
import org.apache.commons.lang3.StringUtils

class MemoryPlugin(private val name: String) : Plugin {
    override fun getAuthor(): String {
        return StringUtils.EMPTY
    }

    override fun getName(): String {
        return name
    }

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return emptyList()
    }
}