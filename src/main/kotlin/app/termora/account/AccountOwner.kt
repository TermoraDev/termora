package app.termora.account

import app.termora.database.OwnerType
import org.apache.commons.lang3.StringUtils

data class AccountOwner(val id: String, val name: String, val type: OwnerType, val role: String) {
    constructor(id: String, name: String, type: OwnerType) : this(id, name, type, StringUtils.EMPTY)

    fun isVisitorMode(): Boolean {
        return type == OwnerType.Team && role == TeamRole.Visitor.name
    }
}