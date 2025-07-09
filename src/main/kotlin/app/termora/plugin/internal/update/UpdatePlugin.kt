package app.termora.plugin.internal.update

import app.termora.ApplicationRunnerExtension
import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin

internal class UpdatePlugin : InternalPlugin() {

    init {
        support.addExtension(ApplicationRunnerExtension::class.java) { MyApplicationRunnerExtension.instance }
    }

    override fun getName(): String {
        return "Update"
    }


    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }
}