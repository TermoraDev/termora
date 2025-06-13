package app.termora.snippet

import app.termora.Icons
import app.termora.tree.SimpleTreeNode
import com.formdev.flatlaf.icons.FlatTreeClosedIcon
import com.formdev.flatlaf.icons.FlatTreeOpenIcon
import javax.swing.Icon

class SnippetTreeNode(snippet: Snippet) : SimpleTreeNode<Snippet>(snippet) {

    override val folderCount: Int
        get() = children().toList().count { if (it is SnippetTreeNode) it.data.type == SnippetType.Folder else false }
    override val id get() = data.id
    override val isFolder get() = data.type == SnippetType.Folder

    override fun toString(): String {
        return data.name
    }

    override fun getIcon(selected: Boolean, expanded: Boolean, hasFocus: Boolean): Icon {
        return when (data.type) {
            SnippetType.Folder -> if (expanded) FlatTreeOpenIcon() else FlatTreeClosedIcon()
            else -> if (selected && hasFocus) Icons.codeSpan.dark else Icons.codeSpan
        }
    }
}