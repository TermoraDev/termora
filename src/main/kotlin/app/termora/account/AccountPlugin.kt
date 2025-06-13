package app.termora.account

import app.termora.ApplicationRunnerExtension
import app.termora.SettingsOptionExtension
import app.termora.database.DatabaseChangedExtension
import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin

internal class AccountPlugin : InternalPlugin() {
    init {
        support.addExtension(SettingsOptionExtension::class.java) { AccountSettingsOptionExtension.instance }
        support.addExtension(ApplicationRunnerExtension::class.java) { AccountManager.AccountApplicationRunnerExtension.instance }
        support.addExtension(ApplicationRunnerExtension::class.java) { AccountManager.getInstance() }
        support.addExtension(ApplicationRunnerExtension::class.java) { PushService.getInstance() }
        support.addExtension(ApplicationRunnerExtension::class.java) { PullService.getInstance() }
        support.addExtension(DatabaseChangedExtension::class.java) { PushService.getInstance() }
    }

    override fun getName(): String {
        return "Account"
    }


    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }
}