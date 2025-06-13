package app.termora.plugins.sync

import app.termora.ApplicationScope
import app.termora.database.DatabaseManager
import org.apache.commons.lang3.StringUtils

/**
 * 同步配置
 */
class SyncProperties private constructor(databaseManager: DatabaseManager) :
    DatabaseManager.IProperties(databaseManager, "Setting.Sync") {

    companion object {
        fun getInstance(): SyncProperties {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(SyncProperties::class) { SyncProperties(DatabaseManager.getInstance()) }
        }
    }

    private inner class SyncTypePropertyDelegate(defaultValue: SyncType) :
        PropertyDelegate<SyncType>(defaultValue) {
        override fun convertValue(value: String): SyncType {
            return try {
                SyncType.valueOf(value)
            } catch (_: Exception) {
                initializer.invoke()
            }
        }
    }

    /**
     * 同步类型
     */
    var type by SyncTypePropertyDelegate(SyncType.GitHub)

    /**
     * 范围
     */
    var rangeHosts by BooleanPropertyDelegate(true)
    var rangeKeyPairs by BooleanPropertyDelegate(true)
    var rangeSnippets by BooleanPropertyDelegate(true)
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

    /**
     * 同步策略，为空就是默认手动
     */
    var policy by StringPropertyDelegate(StringUtils.EMPTY)
}