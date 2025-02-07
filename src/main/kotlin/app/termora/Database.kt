package app.termora

import app.termora.Application.ohMyJson
import app.termora.highlight.KeywordHighlight
import app.termora.keymap.Keymap
import app.termora.keymgr.OhKeyPair
import app.termora.macro.Macro
import app.termora.sync.SyncType
import app.termora.terminal.CursorStyle
import jetbrains.exodus.bindings.StringBinding
import jetbrains.exodus.env.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration.Companion.minutes

class Database private constructor(private val env: Environment) : Disposable {
    companion object {
        private const val KEYMAP_STORE = "Keymap"
        private const val HOST_STORE = "Host"
        private const val KEYWORD_HIGHLIGHT_STORE = "KeywordHighlight"
        private const val MACRO_STORE = "Macro"
        private const val KEY_PAIR_STORE = "KeyPair"
        private val log = LoggerFactory.getLogger(Database::class.java)


        private fun open(dir: File): Database {
            val config = EnvironmentConfig()
            // 32MB
            config.setLogFileSize(1024 * 32)
            config.setGcEnabled(true)
            // 5m
            config.setGcStartIn(5.minutes.inWholeMilliseconds.toInt())
            val environment = Environments.newInstance(dir, config)
            return Database(environment)
        }

        fun getDatabase(): Database {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(Database::class) { open(Application.getDatabaseFile()) }
        }
    }

    val properties by lazy { Properties() }
    val safetyProperties by lazy { SafetyProperties("Setting.SafetyProperties") }
    val terminal by lazy { Terminal() }
    val appearance by lazy { Appearance() }
    val sync by lazy { Sync() }

    private val doorman get() = Doorman.getInstance()


    fun getKeymaps(): Collection<Keymap> {
        val array = env.computeInTransaction { tx ->
            openCursor<String>(tx, KEYMAP_STORE) { _, value ->
                value
            }.values
        }

        val keymaps = mutableListOf<Keymap>()
        for (text in array.iterator()) {
            keymaps.add(Keymap.fromJSON(text) ?: continue)
        }

        return keymaps
    }

    fun addKeymap(keymap: Keymap) {
        env.executeInTransaction {
            put(it, KEYMAP_STORE, keymap.name, keymap.toJSON())
            if (log.isDebugEnabled) {
                log.debug("Added Keymap: ${keymap.name}")
            }
        }
    }

    fun removeKeymap(name: String) {
        env.executeInTransaction {
            delete(it, KEYMAP_STORE, name)
            if (log.isDebugEnabled) {
                log.debug("Removed Keymap: $name")
            }
        }
    }


    fun getHosts(): Collection<Host> {
        val isWorking = doorman.isWorking()
        return env.computeInTransaction { tx ->
            openCursor<Host>(tx, HOST_STORE) { _, value ->
                if (isWorking)
                    ohMyJson.decodeFromString(doorman.decrypt(value))
                else
                    ohMyJson.decodeFromString(value)
            }.values
        }
    }

    fun removeAllHost() {
        env.executeInTransaction { tx ->
            val store = env.openStore(HOST_STORE, StoreConfig.WITHOUT_DUPLICATES_WITH_PREFIXING, tx)
            store.openCursor(tx).use {
                while (it.next) {
                    it.deleteCurrent()
                }
            }
        }
    }

    fun removeAllKeyPair() {
        env.executeInTransaction { tx ->
            val store = env.openStore(KEY_PAIR_STORE, StoreConfig.WITHOUT_DUPLICATES_WITH_PREFIXING, tx)
            store.openCursor(tx).use {
                while (it.next) {
                    it.deleteCurrent()
                }
            }
        }
    }

    fun getKeyPairs(): Collection<OhKeyPair> {
        val isWorking = doorman.isWorking()
        return env.computeInTransaction { tx ->
            openCursor<OhKeyPair>(tx, KEY_PAIR_STORE) { _, value ->
                if (isWorking)
                    ohMyJson.decodeFromString(doorman.decrypt(value))
                else
                    ohMyJson.decodeFromString(value)
            }.values
        }
    }

    fun addHost(host: Host) {
        var text = ohMyJson.encodeToString(host)
        if (doorman.isWorking()) {
            text = doorman.encrypt(text)
        }
        env.executeInTransaction {
            put(it, HOST_STORE, host.id, text)
            if (log.isDebugEnabled) {
                log.debug("Added Host: ${host.id} , ${host.name}")
            }
        }
    }

    fun removeHost(id: String) {
        env.executeInTransaction {
            delete(it, HOST_STORE, id)
            if (log.isDebugEnabled) {
                log.debug("Removed Host: $id")
            }
        }
    }

    fun getKeywordHighlights(): Collection<KeywordHighlight> {
        return env.computeInTransaction { tx ->
            openCursor<KeywordHighlight>(tx, KEYWORD_HIGHLIGHT_STORE) { _, value ->
                ohMyJson.decodeFromString(value)
            }.values
        }
    }

    fun addKeywordHighlight(keywordHighlight: KeywordHighlight) {
        val text = ohMyJson.encodeToString(keywordHighlight)
        env.executeInTransaction {
            put(it, KEYWORD_HIGHLIGHT_STORE, keywordHighlight.id, text)
            if (log.isDebugEnabled) {
                log.debug("Added keyword highlight: ${keywordHighlight.id} , ${keywordHighlight.keyword}")
            }
        }
    }

    fun removeKeywordHighlight(id: String) {
        env.executeInTransaction {
            delete(it, KEYWORD_HIGHLIGHT_STORE, id)
            if (log.isDebugEnabled) {
                log.debug("Removed keyword highlight: $id")
            }
        }
    }

    fun getMacros(): Collection<Macro> {
        return env.computeInTransaction { tx ->
            openCursor<Macro>(tx, MACRO_STORE) { _, value ->
                ohMyJson.decodeFromString(value)
            }.values
        }
    }

    fun addMacro(macro: Macro) {
        val text = ohMyJson.encodeToString(macro)
        env.executeInTransaction {
            put(it, MACRO_STORE, macro.id, text)
            if (log.isDebugEnabled) {
                log.debug("Added macro: ${macro.id}")
            }
        }
    }

    fun removeMacro(id: String) {
        env.executeInTransaction {
            delete(it, MACRO_STORE, id)
            if (log.isDebugEnabled) {
                log.debug("Removed macro: $id")
            }
        }
    }

    fun addKeyPair(key: OhKeyPair) {
        var text = ohMyJson.encodeToString(key)
        if (doorman.isWorking()) {
            text = doorman.encrypt(text)
        }
        env.executeInTransaction {
            put(it, KEY_PAIR_STORE, key.id, text)
            if (log.isDebugEnabled) {
                log.debug("Added Key Pair: ${key.id} , ${key.name}")
            }
        }
    }

    fun removeKeyPair(id: String) {
        env.executeInTransaction {
            delete(it, KEY_PAIR_STORE, id)
            if (log.isDebugEnabled) {
                log.debug("Removed Key Pair: $id")
            }
        }
    }

    private fun put(tx: Transaction, name: String, key: String, value: String) {
        val store = env.openStore(name, StoreConfig.WITHOUT_DUPLICATES_WITH_PREFIXING, tx)
        val k = StringBinding.stringToEntry(key)
        val v = StringBinding.stringToEntry(value)
        store.put(tx, k, v)
    }

    private fun delete(tx: Transaction, name: String, key: String) {
        val store = env.openStore(name, StoreConfig.WITHOUT_DUPLICATES_WITH_PREFIXING, tx)
        val k = StringBinding.stringToEntry(key)
        store.delete(tx, k)
    }

    fun getSafetyProperties(): List<SafetyProperties> {
        return listOf(sync, safetyProperties)
    }

    private inline fun <reified T> openCursor(
        tx: Transaction,
        name: String,
        callback: (key: String, value: String) -> T
    ): Map<String, T> {
        val store = env.openStore(name, StoreConfig.WITHOUT_DUPLICATES_WITH_PREFIXING, tx)
        val map = mutableMapOf<String, T>()
        store.openCursor(tx).use {
            while (it.next) {
                try {
                    val key = StringBinding.entryToString(it.key)
                    map[key] = callback.invoke(
                        key,
                        StringBinding.entryToString(it.value)
                    )
                } catch (e: Exception) {
                    if (log.isWarnEnabled) {
                        log.warn("Decode data failed. data: {}", it.value, e)
                    }
                }
            }
        }
        return map
    }

    private fun putString(name: String, map: Map<String, String>) {
        return env.computeInTransaction {
            val store = env.openStore(name, StoreConfig.WITHOUT_DUPLICATES_WITH_PREFIXING, it)
            for ((key, value) in map.entries) {
                store.put(it, StringBinding.stringToEntry(key), StringBinding.stringToEntry(value))
            }
        }
    }

    private fun getString(name: String, key: String): String? {
        return env.computeInTransaction {
            val store = env.openStore(name, StoreConfig.WITHOUT_DUPLICATES_WITH_PREFIXING, it)
            val value = store.get(it, StringBinding.stringToEntry(key))
            if (value == null) null else StringBinding.entryToString(value)
        }
    }


    abstract inner class Property(private val name: String) {
        private val properties = Collections.synchronizedMap(mutableMapOf<String, String>())

        init {
            @Suppress("OPT_IN_USAGE")
            GlobalScope.launch(Dispatchers.IO) { properties.putAll(getProperties()) }
        }

        protected open fun getString(key: String): String? {
            if (properties.containsKey(key)) {
                return properties[key]
            }
            return getString(name, key)
        }

        open fun getProperties(): Map<String, String> {
            return env.computeInTransaction { tx ->
                openCursor<String>(
                    tx,
                    name
                ) { _, value -> value }
            }
        }

        protected open fun putString(key: String, value: String) {
            properties[key] = value
            putString(name, mapOf(key to value))
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
                try {
                    return CursorStyle.valueOf(value)
                } catch (e: Exception) {
                    return initializer.invoke()
                }
            }
        }


        protected inner class SyncTypePropertyDelegate(defaultValue: SyncType) :
            PropertyDelegate<SyncType>(defaultValue) {
            override fun convertValue(value: String): SyncType {
                try {
                    return SyncType.valueOf(value)
                } catch (e: Exception) {
                    return initializer.invoke()
                }
            }
        }

    }


    /**
     * 终端设置
     */
    inner class Terminal : Property("Setting.Terminal") {

        /**
         * 字体
         */
        var font by StringPropertyDelegate("JetBrains Mono")

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
    }

    /**
     * 通用属性
     */
    inner class Properties : Property("Setting.Properties") {
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
     * 安全的通用属性
     */
    open inner class SafetyProperties(name: String) : Property(name) {
        private val doorman get() = Doorman.getInstance()

        public override fun getString(key: String): String? {
            var value = super.getString(key)
            if (value != null && doorman.isWorking()) {
                try {
                    value = doorman.decrypt(value)
                } catch (e: Exception) {
                    if (log.isWarnEnabled) {
                        log.warn("decryption key: [{}], value: [{}] failed: {}", key, value, e.message)
                    }
                }
            }
            return value
        }


        override fun getProperties(): Map<String, String> {
            val properties = super.getProperties()
            val map = mutableMapOf<String, String>()
            if (doorman.isWorking()) {
                for ((k, v) in properties) {
                    try {
                        map[k] = doorman.decrypt(v)
                    } catch (e: Exception) {
                        if (log.isWarnEnabled) {
                            log.warn("decryption key: [{}], value: [{}] failed: {}", k, v, e.message)
                        }
                    }
                }
            } else {
                map.putAll(properties)
            }
            return map
        }

        fun getString(key: String, defaultValue: String): String {
            return getString(key) ?: defaultValue
        }

        public override fun putString(key: String, value: String) {
            val v = if (doorman.isWorking()) doorman.encrypt(value) else value
            super.putString(key, v)
        }


    }

    /**
     * 外观
     */
    inner class Appearance : Property("Setting.Appearance") {


        /**
         * 外观
         */
        var theme by StringPropertyDelegate("Light")

        /**
         * 跟随系统
         */
        var followSystem by BooleanPropertyDelegate(true)
        var darkTheme by StringPropertyDelegate("Dark")
        var lightTheme by StringPropertyDelegate("Light")

        /**
         * 语言
         */
        var language by StringPropertyLazyDelegate {
            I18n.containsLanguage(Locale.getDefault()) ?: Locale.US.toString()
        }

    }

    /**
     * 同步配置
     */
    inner class Sync : SafetyProperties("Setting.Sync") {
        /**
         * 同步类型
         */
        var type by SyncTypePropertyDelegate(SyncType.GitHub)

        /**
         * 范围
         */
        var rangeHosts by BooleanPropertyDelegate(true)
        var rangeKeyPairs by BooleanPropertyDelegate(true)
        var rangeKeywordHighlights by BooleanPropertyDelegate(true)
        var rangeMacros by BooleanPropertyDelegate(true)
        var rangeKeymap by BooleanPropertyDelegate(true)

        /**
         * Token
         */
        var token by StringPropertyDelegate(String())

        /**
         * Gist ID
         */
        var gist by StringPropertyDelegate(String())

        /**
         * Domain
         */
        var domain by StringPropertyDelegate(String())

        /**
         * 最后同步时间
         */
        var lastSyncTime by LongPropertyDelegate(0L)
    }

    override fun dispose() {
        IOUtils.closeQuietly(env)
    }
}

