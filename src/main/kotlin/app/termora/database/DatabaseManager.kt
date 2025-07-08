package app.termora.database

import app.termora.*
import app.termora.Application.ohMyJson
import app.termora.account.Account
import app.termora.account.AccountExtension
import app.termora.account.AccountManager
import app.termora.account.AccountOwner
import app.termora.database.Data.Companion.toData
import app.termora.highlight.KeywordHighlightManager
import app.termora.keymap.KeymapManager
import app.termora.keymgr.KeyManager
import app.termora.macro.MacroManager
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import app.termora.snippet.SnippetManager
import app.termora.terminal.CursorStyle
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class DatabaseManager private constructor() : Disposable {
    companion object {
        val log: Logger = LoggerFactory.getLogger(DatabaseManager::class.java)

        fun getInstance(): DatabaseManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(DatabaseManager::class) { DatabaseManager() }
        }
    }

    val database: Database
    val lock = ReentrantLock()

    val properties by lazy { Properties(this) }
    val terminal by lazy { Terminal(this) }
    val appearance by lazy { Appearance(this) }
    val sftp by lazy { SFTP(this) }


    private val map = Collections.synchronizedMap<String, String?>(mutableMapOf())
    private val accountManager get() = AccountManager.getInstance()

    init {

        val databaseFile = FileUtils.getFile(
            Application.getBaseDataDir(),
            "config", "termora.db"
        )
        FileUtils.forceMkdirParent(databaseFile)
        val isExists = databaseFile.exists()

        database = Database.connect(
            "jdbc:sqlite:${databaseFile.absolutePath}",
            driver = "org.sqlite.JDBC", user = "sa"
        )

        // 没有加密的表优先创建
        if (isExists.not()) {
            transaction(database) { SchemaUtils.create(UnsafeSettingEntity) }
        }

        // 获取密钥信息
        transaction(database) { DatabaseSecret.getInstance(database) }

        // 设置数据库版本号，便于后续升级
        if (isExists.not()) {
            transaction(database) {
                // 创建数据库
                SchemaUtils.create(DataEntity, SettingEntity)
                @Suppress("SqlNoDataSourceInspection")
                exec("PRAGMA db_version = 1", explicitStatementType = StatementType.UPDATE)
            }
        }

        // 异步初始化
        Thread.ofVirtual().start { map.putAll(getSettings()); }

        // 注册动态扩展
        registerDynamicExtensions()

    }

    private fun registerDynamicExtensions() {
        // 负责清理或转移数据，如果从本地用户切换到云端用户，那么把本地用户的数据复制到云端用户下，然后本地用户数据清除
        DynamicExtensionHandler.getInstance().register(AccountExtension::class.java, AccountDataTransferExtension())
            .let { Disposer.register(this, it) }


        // 用户团队变更
        DynamicExtensionHandler.getInstance().register(AccountExtension::class.java, AccountTeamChangedExtension())
            .let { Disposer.register(this, it) }
    }

    /**
     * 返回本地所有用户的数据，调用者需要过滤具体用户
     */
    inline fun <reified T> data(type: IDataType): List<T> {
        return data(type, StringUtils.EMPTY)
    }

    /**
     * 返回本地所有用户的数据，调用者需要过滤具体用户
     */
    inline fun <reified T> data(type: IDataType, ownerId: String): List<T> {
        val list = mutableListOf<T>()
        try {
            for (data in rawData(type, ownerId)) {
                list.add(ohMyJson.decodeFromString<T>(data.data))
            }
        } catch (e: Exception) {
            if (log.isWarnEnabled) {
                log.warn(e.message, e)
            }
        }
        return list
    }

    /**
     * 返回本地所有用户的数据，调用者需要过滤具体用户
     */
    fun data(id: String): Data? {
        return lock.withLock {
            transaction(database) {
                DataEntity.selectAll()
                    .where { (DataEntity.id.eq(id)) }
                    .firstOrNull()?.toData()
            }
        }
    }


    fun unsyncedData(): List<Data> {
        val list = mutableListOf<Data>()
        lock.withLock {
            transaction(database) {
                val rows = DataEntity.selectAll().where { (DataEntity.synced eq false) }.toList()
                for (row in rows) {
                    list.add(row.toData())
                }
            }
        }

        if (accountManager.isLocally().not()) {
            val ownerIds = accountManager.getOwnerIds()
            return list.filter { ownerIds.contains(it.ownerId) }
                .filterNot { AccountManager.isLocally(it.ownerId) }
        }

        return list
    }

    /**
     * 获取数据版本
     */
    fun version(id: String): Long? {
        return lock.withLock {
            transaction(database) {
                DataEntity.select(DataEntity.version)
                    .where { (DataEntity.id.eq(id) and DataEntity.deleted.eq(false)) }
                    .firstOrNull()?.get(DataEntity.version) ?: 0
            }
        }
    }

    /**
     * 不会返回已删除的数据
     */
    fun rawData(type: IDataType): List<Data> {
        return rawData(type, StringUtils.EMPTY)
    }

    /**
     * 不会返回已删除的数据
     */
    fun rawData(type: IDataType, ownerId: String): List<Data> {
        val list = mutableListOf<Data>()
        lock.withLock {
            transaction(database) {
                val query = DataEntity.selectAll()
                    .where { (DataEntity.type eq type.dataType()) and (DataEntity.deleted.eq(false)) }

                if (ownerId.isNotBlank()) {
                    query.andWhere { DataEntity.ownerId eq ownerId }
                }

                for (row in query) {
                    try {
                        list.add(row.toData())
                    } catch (e: Exception) {
                        if (log.isWarnEnabled) {
                            log.warn(e.message, e)
                        }
                    }
                }
            }
        }
        return list
    }

    /**
     * 存在则获取本地版本 +1 然后修改，synced 会改成 false ，不存在则新增
     */
    fun saveAndIncrementVersion(
        data: Data,
        source: DatabaseChangedExtension.Source = DatabaseChangedExtension.Source.User
    ) {
        val oldData = data(data.id)
        if (oldData != null) {
            // 已经删除的数据，将不处理
            if (oldData.deleted) {
                return
            }

            lock.withLock {
                transaction(database) {
                    DataEntity.update({ (DataEntity.id eq data.id) }) {
                        it[DataEntity.data] = data.data
                        it[DataEntity.version] = oldData.version + 1
                        it[DataEntity.synced] = false
                    }
                }
            }

            // 触发更改
            DatabaseChangedExtension.fireDataChanged(
                data.id,
                data.type,
                DatabaseChangedExtension.Action.Changed,
                source
            )
        } else {
            save(data, source)
        }
    }

    fun save(data: Data, source: DatabaseChangedExtension.Source = DatabaseChangedExtension.Source.User) {
        var action = DatabaseChangedExtension.Action.Changed
        lock.withLock {
            transaction(database) {
                val exists = DataEntity.selectAll()
                    .where { (DataEntity.id eq data.id) }
                    .any()

                if (exists) {
                    DataEntity.update({ (DataEntity.id eq data.id) }) {
                        it[DataEntity.data] = data.data
                        it[DataEntity.version] = data.version
                        it[DataEntity.synced] = data.synced
                        it[DataEntity.deleted] = data.deleted
                    }
                    action = DatabaseChangedExtension.Action.Changed
                } else {
                    DataEntity.insert {
                        it[DataEntity.id] = data.id
                        it[DataEntity.ownerId] = data.ownerId
                        it[DataEntity.ownerType] = data.ownerType
                        it[DataEntity.type] = data.type
                        it[DataEntity.data] = data.data
                        it[DataEntity.synced] = data.synced
                        it[DataEntity.deleted] = data.deleted
                        it[DataEntity.version] = data.version
                    }
                    action = DatabaseChangedExtension.Action.Added
                }
            }
        }

        // 触发更改
        DatabaseChangedExtension.fireDataChanged(data.id, data.type, action, source)
    }

    fun delete(
        id: String,
        type: String,
        source: DatabaseChangedExtension.Source = DatabaseChangedExtension.Source.User
    ) {

        lock.withLock {
            transaction(database) {
                DataEntity.update({ DataEntity.id eq id }) {
                    it[DataEntity.deleted] = true
                    // 如果是本地用户，那么删除是不需要同步的，云端用户才需要同步
                    // 云端用户也会判断，如果来源 Sync 那么默认同步了
                    it[DataEntity.synced] = accountManager.isLocally()
                    it[DataEntity.data] = StringUtils.EMPTY
                }
            }
        }

        // 触发更改
        DatabaseChangedExtension.fireDataChanged(id, type, DatabaseChangedExtension.Action.Removed, source)
    }

    fun getSettings(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        lock.withLock {
            transaction(database) {
                for (row in SettingEntity.selectAll().toList()) {
                    map[row[SettingEntity.name]] = row[SettingEntity.value]
                }
            }
        }
        return map
    }

    fun setSetting(name: String, value: String) {
        lock.withLock {
            transaction(database) {
                for (row in SettingEntity.selectAll().where { SettingEntity.name eq name }.toList()) {
                    SettingEntity.deleteWhere { SettingEntity.id eq row[SettingEntity.id] }
                }
                SettingEntity.insert {
                    it[SettingEntity.name] = name
                    it[SettingEntity.value] = value
                }
            }
            map[name] = value
        }
    }

    fun getSetting(name: String): String? {
        if (map.containsKey(name)) {
            return map[name]
        }
        lock.withLock {
            transaction(database) {
                map[name] = SettingEntity.selectAll()
                    .where { SettingEntity.name eq name }.toList()
                    .singleOrNull()?.getOrNull(SettingEntity.value)
            }
        }
        return map[name]
    }

    override fun dispose() {
        lock.withLock {
            TransactionManager.closeAndUnregister(database)
        }
    }


    private inner class AccountDataTransferExtension : AccountExtension {
        override fun onAccountChanged(oldAccount: Account, newAccount: Account) {
            if (oldAccount.isLocally && newAccount.isLocally) {
                return
            }

            if (oldAccount.id == newAccount.id) {
                return
            }

            // 如果之前是本地用户，现在是云端用户，那么把之前的数据复制一份到云端用户
            // 复制到云端之后就可以删除本地数据了
            if (oldAccount.isLocally && newAccount.isLocally.not()) {
                transferData(newAccount)
            }

            // 如果之前是云端用户，退出登录了要删除本地数据
            if (oldAccount.isLocally.not()) {
                lock.withLock {
                    transaction(database) {
                        // 删除用户的数据
                        DataEntity.deleteWhere {
                            DataEntity.ownerId.eq(oldAccount.id) and (DataEntity.ownerType.eq(OwnerType.User.name))
                        }
                        // 删除团队的数据
                        for (team in oldAccount.teams) {
                            DataEntity.deleteWhere {
                                DataEntity.ownerId.eq(team.id) and (DataEntity.ownerType.eq(OwnerType.Team.name))
                            }
                        }
                        DatabaseChangedExtension.fireDataChanged(
                            StringUtils.EMPTY,
                            StringUtils.EMPTY,
                            DatabaseChangedExtension.Action.Removed
                        )
                    }
                }
            }
        }

        private fun silentDelete(id: String) {
            lock.withLock {
                transaction(database) {
                    DataEntity.deleteWhere { DataEntity.id.eq(id) }
                }
            }
        }

        private fun transferData(account: Account) {
            val hostManager = HostManager.getInstance()
            val snippetManager = SnippetManager.getInstance()
            val macroManager = MacroManager.getInstance()
            val keymapManager = KeymapManager.getInstance()
            val keyManager = KeyManager.getInstance()
            val highlightManager = KeywordHighlightManager.getInstance()
            val accountOwner = AccountOwner(
                id = account.id,
                name = account.email,
                type = OwnerType.User
            )

            for (host in hostManager.hosts()) {
                // 已经删除，则忽略
                if (host.deleted) continue
                // 不是用户数据，那么忽略
                if (host.ownerType.isNotBlank() && host.ownerType != OwnerType.User.name) continue
                // 不是本地用户数据，那么忽略
                if (AccountManager.isLocally(host.ownerId).not()) continue
                // 先删除，因为 ID 没有改变，改变的只是 owner 信息
                silentDelete(host.id)
                hostManager.addHost(host.copy(ownerId = accountOwner.id, ownerType = accountOwner.type.name))
            }

            for (snippet in snippetManager.snippets()) {
                if (snippet.deleted) continue
                silentDelete(snippet.id)
                snippetManager.addSnippet(snippet)
            }

            for (macro in macroManager.getMacros()) {
                silentDelete(macro.id)
                macroManager.addMacro(macro)
            }

            for (keymap in keymapManager.getKeymaps()) {
                silentDelete(keymap.id)
                keymapManager.addKeymap(keymap)
            }

            for (keypair in keyManager.getOhKeyPairs()) {
                silentDelete(keypair.id)
                keyManager.addOhKeyPair(keypair, accountOwner)
            }

            for (e in highlightManager.getKeywordHighlights()) {
                silentDelete(e.id)
                highlightManager.addKeywordHighlight(e, accountOwner)
            }

        }

        override fun ordered(): Long {
            return 0
        }

    }

    private inner class AccountTeamChangedExtension : AccountExtension {

        override fun onAccountChanged(oldAccount: Account, newAccount: Account) {
            if (oldAccount.isLocally && newAccount.isLocally) {
                return
            }

            // 如果团队变更，那么删除所有旧的团队数据，静默删除
            if (oldAccount.id == newAccount.id) {
                if (oldAccount.teams != newAccount.teams) {
                    for (team in oldAccount.teams) {
                        lock.withLock {
                            transaction(database) {
                                DataEntity.deleteWhere {
                                    DataEntity.ownerId.eq(team.id) and (DataEntity.ownerType.eq(
                                        OwnerType.Team.name
                                    ))
                                }
                            }
                        }
                    }
                }
            }

        }

        override fun ordered(): Long {
            return -1
        }

    }

    abstract class IProperties(
        private val databaseManager: DatabaseManager,
        private val name: String
    ) {

        private val map get() = databaseManager.map

        protected open fun getString(key: String): String? {
            return databaseManager.getSetting("${name}.$key")
        }


        protected open fun putString(key: String, value: String) {
            databaseManager.setSetting("${name}.$key", value)
            // 触发变动
            DatabasePropertiesChangedExtension.onPropertyChanged(name, key, value)
        }


        fun getProperties(): Map<String, String> {
            val properties = mutableMapOf<String, String>()
            for (e in map.entries) {
                if (e.key.startsWith("${name}.")) {
                    properties[e.key] = e.value ?: continue
                }
            }
            return properties
        }


        protected abstract inner class PropertyLazyDelegate<T>(protected val initializer: () -> T) :
            ReadWriteProperty<Any?, T> {
            private var value: T? = null

            override fun getValue(thisRef: Any?, property: KProperty<*>): T {
                if (value == null) {
                    val v = getString(property.name)
                    value = if (v == null) {
                        initializer.invoke()
                    } else {
                        convertValue(v)
                    }
                }

                if (value == null) {
                    value = initializer.invoke()
                }
                return value!!
            }

            abstract fun convertValue(value: String): T

            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                this.value = value
                putString(property.name, value.toString())
            }

        }

        protected abstract inner class PropertyDelegate<T>(private val defaultValue: T) :
            PropertyLazyDelegate<T>({ defaultValue })


        protected inner class StringPropertyDelegate(defaultValue: String) :
            PropertyDelegate<String>(defaultValue) {
            override fun convertValue(value: String): String {
                return value
            }
        }

        protected inner class IntPropertyDelegate(defaultValue: Int) :
            PropertyDelegate<Int>(defaultValue) {
            override fun convertValue(value: String): Int {
                return value.toIntOrNull() ?: initializer.invoke()
            }
        }

        protected inner class DoublePropertyDelegate(defaultValue: Double) :
            PropertyDelegate<Double>(defaultValue) {
            override fun convertValue(value: String): Double {
                return value.toDoubleOrNull() ?: initializer.invoke()
            }
        }


        protected inner class LongPropertyDelegate(defaultValue: Long) :
            PropertyDelegate<Long>(defaultValue) {
            override fun convertValue(value: String): Long {
                return value.toLongOrNull() ?: initializer.invoke()
            }
        }

        protected inner class BooleanPropertyDelegate(defaultValue: Boolean) :
            PropertyDelegate<Boolean>(defaultValue) {
            override fun convertValue(value: String): Boolean {
                return value.toBooleanStrictOrNull() ?: initializer.invoke()
            }
        }

        protected open inner class StringPropertyLazyDelegate(initializer: () -> String) :
            PropertyLazyDelegate<String>(initializer) {
            override fun convertValue(value: String): String {
                return value
            }
        }


        protected inner class CursorStylePropertyDelegate(defaultValue: CursorStyle) :
            PropertyDelegate<CursorStyle>(defaultValue) {
            override fun convertValue(value: String): CursorStyle {
                return try {
                    CursorStyle.valueOf(value)
                } catch (_: Exception) {
                    initializer.invoke()
                }
            }
        }


    }

    /**
     * 终端设置
     */
    class Terminal(databaseManager: DatabaseManager) : IProperties(databaseManager, "Setting.Terminal") {

        /**
         * 字体
         */
        var font by StringPropertyDelegate("JetBrains Mono")

        /**
         * 回退字体
         */
        var fallbackFont by StringPropertyDelegate(StringUtils.EMPTY)

        /**
         * 默认终端
         */
        var localShell by StringPropertyLazyDelegate { Application.getDefaultShell() }

        /**
         * 字体大小
         */
        var fontSize by IntPropertyDelegate(14)

        /**
         * 最大行数
         */
        var maxRows by IntPropertyDelegate(5000)

        /**
         * 调试模式
         */
        var debug by BooleanPropertyDelegate(false)

        /**
         * 蜂鸣声
         */
        var beep by BooleanPropertyDelegate(true)

        /**
         * 超链接
         */
        var hyperlink by BooleanPropertyDelegate(true)

        /**
         * 光标闪烁
         */
        var cursorBlink by BooleanPropertyDelegate(false)

        /**
         * 选中复制
         */
        var selectCopy by BooleanPropertyDelegate(false)

        /**
         * 光标样式
         */
        var cursor by CursorStylePropertyDelegate(CursorStyle.Block)

        /**
         * 终端断开连接时自动关闭Tab
         */
        var autoCloseTabWhenDisconnected by BooleanPropertyDelegate(false)

        /**
         * 是否显示悬浮工具栏
         */
        var floatingToolbar by BooleanPropertyDelegate(true)
    }

    /**
     * 通用属性
     */
    class Properties(databaseManager: DatabaseManager) : IProperties(databaseManager, "Setting.Properties") {
        public override fun getString(key: String): String? {
            return super.getString(key)
        }


        fun getString(key: String, defaultValue: String): String {
            return getString(key) ?: defaultValue
        }

        public override fun putString(key: String, value: String) {
            super.putString(key, value)
        }
    }

    /**
     * 外观
     */
    class Appearance(databaseManager: DatabaseManager) : IProperties(databaseManager, "Setting.Appearance") {


        /**
         * 外观
         */
        var theme by StringPropertyDelegate("Light")

        /**
         * 布局
         */
        var layout by StringPropertyDelegate(TermoraLayout.Screen.name)

        /**
         * 跟随系统
         */
        var followSystem by BooleanPropertyDelegate(true)
        var darkTheme by StringPropertyDelegate("Dark")
        var lightTheme by StringPropertyDelegate("Light")

        /**
         * 允许后台运行，也就是托盘
         */
        var backgroundRunning by BooleanPropertyDelegate(false)

        /**
         * 关闭 Tab 前询问
         */
        var confirmTabClose by BooleanPropertyDelegate(false)

        /**
         * 背景图片的地址
         */
        var backgroundImage by StringPropertyDelegate(StringUtils.EMPTY)

        /**
         * 语言
         */
        var language by StringPropertyLazyDelegate {
            I18n.containsLanguage(Locale.getDefault()) ?: Locale.US.toString()
        }


        /**
         * 透明度
         */
        var opacity by DoublePropertyDelegate(1.0)
    }

    /**
     * SFTP
     */
    class SFTP(databaseManager: DatabaseManager) : IProperties(databaseManager, "Setting.SFTP") {


        /**
         * 编辑命令
         */
        var editCommand by StringPropertyDelegate(StringUtils.EMPTY)


        /**
         * sftp command
         */
        var sftpCommand by StringPropertyDelegate(StringUtils.EMPTY)

        /**
         * defaultDirectory
         */
        var defaultDirectory by StringPropertyDelegate(StringUtils.EMPTY)


        /**
         * 是否固定在标签栏
         */
        var pinTab by BooleanPropertyDelegate(false)

        /**
         * 是否保留原始文件时间
         */
        var preserveModificationTime by BooleanPropertyDelegate(false)

    }

}