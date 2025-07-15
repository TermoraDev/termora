package app.termora.plugins.migration

import app.termora.*
import app.termora.account.AccountManager
import app.termora.account.AccountOwner
import app.termora.database.DatabaseManager
import app.termora.database.OwnerType
import app.termora.highlight.KeywordHighlightManager
import app.termora.keymap.KeymapManager
import app.termora.keymgr.KeyManager
import app.termora.macro.MacroManager
import app.termora.snippet.SnippetManager
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.CountDownLatch
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

class MigrationApplicationRunnerExtension private constructor() : ApplicationRunnerExtension {
    companion object {
        private val log = LoggerFactory.getLogger(MigrationApplicationRunnerExtension::class.java)
        val instance by lazy { MigrationApplicationRunnerExtension() }
    }

    override fun ready() {
        val file = getDatabaseFile()
        if (file.exists().not()) return

        // 如果数据库文件存在，那么需要迁移文件
        val countDownLatch = CountDownLatch(1)

        SwingUtilities.invokeAndWait {
            try {
                // 打开数据
                openDatabase()

                // 尝试解锁
                openDoor()

                // 询问是否迁移
                if (askMigrate()) {

                    // 迁移
                    migrate()

                    // 移动到旧的目录
                    moveOldDirectory()

                    // 重启
                    restart()

                }

            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            } finally {
                countDownLatch.countDown()
            }

        }

        countDownLatch.await()

    }

    private fun openDoor() {
        if (Doorman.getInstance().isWorking()) {
            if (DoormanDialog(null).open().not()) {
                Disposer.dispose(TermoraFrameManager.getInstance())
            }
        }
    }

    private fun openDatabase() {
        try {
            // 初始化数据库
            Database.getDatabase()
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
            JOptionPane.showMessageDialog(
                null, "Unable to open database",
                I18n.getString("termora.title"), JOptionPane.ERROR_MESSAGE
            )
            exitProcess(1)
        }
    }

    private fun migrate() {
        val database = Database.getDatabase()
        val accountManager = AccountManager.getInstance()
        val databaseManager = DatabaseManager.getInstance()
        val ownerId = accountManager.getAccountId()
        val hostManager = HostManager.getInstance()
        val snippetManager = SnippetManager.getInstance()
        val macroManager = MacroManager.getInstance()
        val keymapManager = KeymapManager.getInstance()
        val keyManager = KeyManager.getInstance()
        val highlightManager = KeywordHighlightManager.getInstance()
        val accountOwner = AccountOwner(
            id = accountManager.getAccountId(),
            name = accountManager.getEmail(),
            type = OwnerType.User
        )

        for (host in database.getHosts()) {
            if (host.deleted) continue
            hostManager.addHost(host.copy(ownerId = accountManager.getAccountId(), ownerType = OwnerType.User.name))
        }

        for (snippet in database.getSnippets()) {
            if (snippet.deleted) continue
            snippetManager.addSnippet(snippet)
        }

        for (macro in database.getMacros()) {
            macroManager.addMacro(macro)
        }

        for (keymap in database.getKeymaps()) {
            keymapManager.addKeymap(keymap)
        }

        for (keypair in database.getKeyPairs()) {
            keyManager.addOhKeyPair(keypair, accountOwner)
        }

        for (e in database.getKeywordHighlights()) {
            highlightManager.addKeywordHighlight(e, accountOwner)
        }

        val list = listOf(
            database.sync,
            database.properties,
            database.terminal,
            database.sftp,
            database.appearance,
        )

        for (e in list) {
            for (k in e.getProperties()) {
                databaseManager.setSetting(e.name + "." + k.key, k.value)
            }
        }

        for (e in database.safetyProperties.getProperties()) {
            databaseManager.setSetting(database.properties.name + "." + e.key, e.value)
        }


    }

    private fun askMigrate(): Boolean {

        if (MigrationDialog(null).open()) {
            return true
        }

        // 移动到旧的目录
        moveOldDirectory()

        // 重启
        restart()

        return false
    }

    private fun moveOldDirectory() {
        // 关闭数据库
        Disposer.dispose(Database.getDatabase())

        val file = getDatabaseFile()
        FileUtils.moveDirectory(
            file,
            FileUtils.getFile(file.parentFile, file.name + "-old-" + System.currentTimeMillis())
        )

    }

    private fun restart() {

        // 重启
        TermoraRestarter.getInstance().scheduleRestart(null, ask = false)

    }


    fun getDatabaseFile(): File {
        return FileUtils.getFile(Application.getBaseDataDir(), "storage")
    }
}