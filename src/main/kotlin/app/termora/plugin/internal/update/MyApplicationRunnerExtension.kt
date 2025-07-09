package app.termora.plugin.internal.update

import app.termora.ApplicationRunnerExtension

internal class MyApplicationRunnerExtension private constructor() : ApplicationRunnerExtension {
    companion object {
        val instance = MyApplicationRunnerExtension()
    }

    override fun ready() {
        Updater.getInstance().scheduleUpdate()
    }
}