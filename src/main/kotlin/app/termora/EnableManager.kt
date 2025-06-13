package app.termora

import app.termora.database.DatabaseManager
import app.termora.tree.NewHostTree
import javax.swing.SwingUtilities

class EnableManager private constructor() {

    companion object {
        fun getInstance(): EnableManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(EnableManager::class) { EnableManager() }
        }
    }

    private val properties get() = DatabaseManager.getInstance().properties

    /**
     * [NewHostTree] 是否显示标签
     */
    fun isShowTags() = getFlag("HostTree.showTags", true)
    fun setShowTags(value: Boolean) {
        setFlag("HostTree.showTags", value)
        updateComponentTreeUI()
    }

    /**
     * [NewHostTree] 是否显示更多信息
     */
    fun isShowMoreInfo() = getFlag("HostTree.showMoreInfo", false)
    fun setShowMoreInfo(value: Boolean) {
        setFlag("HostTree.showMoreInfo", value)
        updateComponentTreeUI()
    }

    fun setFlag(key: String, value: Boolean) {
        setFlag(key, value.toString())
    }

    fun getFlag(key: String, defaultValue: Boolean): Boolean {
        return getFlag(key, defaultValue.toString()).toBooleanStrictOrNull() ?: defaultValue
    }


    fun setFlag(key: String, value: Int) {
        setFlag(key, value.toString())
    }

    fun getFlag(key: String, defaultValue: Int): Int {
        return getFlag(key, defaultValue.toString()).toIntOrNull() ?: defaultValue
    }

    fun setFlag(key: String, value: String) {
        properties.putString(key, value)
    }


    fun getFlag(key: String, defaultValue: String): String {
        return properties.getString(key, defaultValue)
    }

    private fun updateComponentTreeUI() {
        // reload all tree
        for (frame in TermoraFrameManager.getInstance().getWindows()) {
            for (tree in SwingUtils.getDescendantsOfClass(NewHostTree::class.java, frame)) {
                SwingUtilities.updateComponentTreeUI(tree)
            }
        }
    }
}