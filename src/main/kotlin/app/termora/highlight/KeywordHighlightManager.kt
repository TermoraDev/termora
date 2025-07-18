package app.termora.highlight

import app.termora.Application.ohMyJson
import app.termora.ApplicationScope
import app.termora.TerminalPanelFactory
import app.termora.account.AccountOwner
import app.termora.database.Data
import app.termora.database.DataType
import app.termora.database.DatabaseManager
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory

class KeywordHighlightManager private constructor() {

    companion object {
        fun getInstance(): KeywordHighlightManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(KeywordHighlightManager::class) { KeywordHighlightManager() }
        }

        private val log = LoggerFactory.getLogger(KeywordHighlightManager::class.java)
    }

    private val database get() = DatabaseManager.getInstance()


    fun addKeywordHighlight(keywordHighlight: KeywordHighlight, accountOwner: AccountOwner) {


        database.saveAndIncrementVersion(
            Data(
                id = keywordHighlight.id,
                ownerId = accountOwner.id,
                ownerType = accountOwner.type.name,
                type = DataType.KeywordHighlight.name,
                data = ohMyJson.encodeToString(keywordHighlight),
            )
        )

        TerminalPanelFactory.getInstance().repaintAll()

        if (log.isDebugEnabled) {
            log.debug("Keyword highlighter added. {}", keywordHighlight)
        }
    }

    fun removeKeywordHighlight(id: String) {
        database.delete(id, DataType.KeywordHighlight.name)
        TerminalPanelFactory.getInstance().repaintAll()

        if (log.isDebugEnabled) {
            log.debug("Keyword highlighter removed. {}", id)
        }
    }

    fun getKeywordHighlights(): List<KeywordHighlight> {
        return getKeywordHighlights(StringUtils.EMPTY)
    }

    fun getKeywordHighlights(ownerId: String): List<KeywordHighlight> {
        return database.data<KeywordHighlight>(DataType.KeywordHighlight, ownerId).sortedBy { it.sort }
    }

}