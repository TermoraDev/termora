package app.termora.tree

import app.termora.Host
import app.termora.Icons
import app.termora.account.Team
import app.termora.database.OwnerType
import javax.swing.Icon

class TeamTreeNode(val team: Team) : HostTreeNode(
    Host(
        id = team.id,
        name = team.name,
        protocol = "Team",
        ownerId = team.id,
        ownerType = OwnerType.Team.name
    )
) {

    override val isFolder: Boolean
        get() = true

    override fun getIcon(selected: Boolean, expanded: Boolean, hasFocus: Boolean): Icon {
        return if (selected && hasFocus) Icons.cwmUsers.dark else Icons.cwmUsers
    }

    override fun toString(): String {
        return team.name
    }
}