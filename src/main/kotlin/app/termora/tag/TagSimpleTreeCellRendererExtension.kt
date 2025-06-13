package app.termora.tag

import app.termora.*
import app.termora.account.Account
import app.termora.account.AccountExtension
import app.termora.account.AccountManager
import app.termora.database.DataType
import app.termora.database.DatabaseChangedExtension
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import app.termora.tree.HostTreeNode
import app.termora.tree.MarkerSimpleTreeCellAnnotation
import app.termora.tree.SimpleTreeCellAnnotation
import app.termora.tree.SimpleTreeCellRendererExtension
import java.awt.Color
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JTree

class TagSimpleTreeCellRendererExtension private constructor() : SimpleTreeCellRendererExtension, Disposable {


    companion object {
        fun getInstance(): TagSimpleTreeCellRendererExtension {
            return ApplicationScope.Companion.forApplicationScope()
                .getOrCreate(TagSimpleTreeCellRendererExtension::class) { TagSimpleTreeCellRendererExtension() }
        }
    }

    private val accountManager get() = AccountManager.getInstance()
    private val tagManager get() = TagManager.getInstance()
    private val tags = mutableMapOf<String, Tag>()
    private val isFirst = AtomicBoolean(false)
    private val isShowTags get() = EnableManager.getInstance().isShowTags()

    init {

        DynamicExtensionHandler.Companion.getInstance()
            .register(SimpleTreeCellRendererExtension::class.java, this)
            .let { Disposer.register(this, it) }


        DynamicExtensionHandler.Companion.getInstance()
            .register(DatabaseChangedExtension::class.java, object : DatabaseChangedExtension {
                override fun onDataChanged(
                    id: String,
                    type: String,
                    action: DatabaseChangedExtension.Action,
                    source: DatabaseChangedExtension.Source
                ) {
                    if (type == DataType.Tag.name) {
                        reload()
                    }
                }
            })
            .let { Disposer.register(this, it) }

        DynamicExtensionHandler.Companion.getInstance()
            .register(AccountExtension::class.java, object : AccountExtension {
                override fun onAccountChanged(oldAccount: Account, newAccount: Account) {
                    if (oldAccount.id != newAccount.id) {
                        reload()
                    }
                }
            })
            .let { Disposer.register(this, it) }
    }


    override fun createAnnotations(
        tree: JTree,
        value: Any?,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): List<SimpleTreeCellAnnotation> {
        if (value !is HostTreeNode) return emptyList()
        if (isShowTags.not()) return emptyList()

        if (isFirst.get().not()) {
            if (isFirst.compareAndSet(false, true)) {
                reload()
            }
        }

        val tags = value.host.options.tags
        val annotations = mutableListOf<SimpleTreeCellAnnotation>()

        for (id in tags) {
            val tag = this.tags[id] ?: continue
            annotations.add(
                MarkerSimpleTreeCellAnnotation(
                    tag.text,
                    foreground = Color.white,
                    background = ColorHash.hash(tag.id),
                )
            )
        }

        return annotations
    }

    private fun reload() {
        val ownerIds = accountManager.getOwnerIds()
        tags.clear()
        for (ownerId in ownerIds) {
            tags.putAll(tagManager.getTags(ownerId).associateBy { it.id })
        }
    }
}