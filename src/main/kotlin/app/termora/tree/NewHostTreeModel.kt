package app.termora.tree

import app.termora.*
import app.termora.account.Account
import app.termora.account.AccountExtension
import app.termora.account.AccountManager
import app.termora.account.ServerSignedExtension
import app.termora.database.DataType
import app.termora.database.DatabaseChangedExtension
import app.termora.database.OwnerType
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import javax.swing.tree.MutableTreeNode
import javax.swing.tree.TreeNode
import kotlin.math.min


class NewHostTreeModel private constructor() : SimpleTreeModel<Host>(
    HostTreeNode(
        Host(
            protocol = "Folder",
            name = "所有主机",
            ownerType = OwnerType.User.name,
        )
    )
), Disposable {

    companion object {
        fun getInstance(): NewHostTreeModel {
            return ApplicationScope.Companion.forApplicationScope()
                .getOrCreate(NewHostTreeModel::class) { NewHostTreeModel() }
        }
    }


    private val hostManager get() = HostManager.Companion.getInstance()
    private val accountManager get() = AccountManager.getInstance()


    init {
        reload()
        registerDynamicExtensions()
    }

    override fun getRoot(): HostTreeNode {
        return super.getRoot() as HostTreeNode
    }


    override fun reload(parent: TreeNode) {

        if (parent !is HostTreeNode) {
            super.reload(parent)
            return
        }

        parent.removeAllChildren()

        val ownerIds = accountManager.getOwnerIds()
        val hosts = hostManager.hosts().filter { ownerIds.contains(it.ownerId) }
        val nodes = linkedMapOf<String, HostTreeNode>()

        // 如果是根，需要引入团队功能
        if (parent == getRoot()) {
            if (accountManager.hasTeamFeature()) {
                for (team in accountManager.getTeams()) {
                    nodes[team.id] = TeamTreeNode(team)
                }
            }

            nodes[accountManager.getAccountId()] = HostTreeNode(
                Host(
                    id = "0",
                    name = I18n.getString("termora.welcome.my-hosts"),
                    ownerId = accountManager.getAccountId(),
                    ownerType = OwnerType.User.name,
                    protocol = "Folder"
                )
            )
        }

        // 遍历 Host 列表，构建树节点
        for (host in hosts) {
            val node = HostTreeNode(host)
            nodes[host.id] = node
        }

        for (host in hosts) {
            val node = nodes[host.id] ?: continue
            val p = if (host.parentId == "0" || host.parentId.isBlank())
                nodes[accountManager.getAccountId()] else nodes[host.parentId]
            if (p == null) continue
            p.add(node)
        }

        if (parent == getRoot()) {
            if (accountManager.hasTeamFeature()) {
                for (team in accountManager.getTeams()) {
                    parent.add(nodes.getValue(team.id))
                }
            }
            parent.add(nodes.getValue(accountManager.getAccountId()))
        } else {
            for (node in nodes.values) {
                if (node.host.parentId == parent.id) {
                    parent.add(node)
                }
            }
        }

        super.reload(parent)
    }

    override fun insertNodeInto(newChild: MutableTreeNode, parent: MutableTreeNode, index: Int) {
        insertNodeInto(newChild, parent, index, true)
    }

    private fun insertNodeInto(newChild: MutableTreeNode, parent: MutableTreeNode, index: Int, flush: Boolean) {
        super.insertNodeInto(newChild, parent, min(index, parent.childCount))

        if (flush.not()) return

        if (newChild is HostTreeNode) {
            hostManager.addHost(newChild.host)
        }

        // 重置所有排序
        if (parent is HostTreeNode) {
            for ((i, c) in parent.children().toList().filterIsInstance<HostTreeNode>().withIndex()) {
                val sort = i.toLong()
                if (c.host.sort == sort) continue
                c.host = c.host.copy(sort = sort)
                hostManager.addHost(c.host)
            }
        }
    }

    override fun nodeStructureChanged(node: TreeNode?) {
        if (node is HostTreeNode) {
            hostManager.addHost(node.host)
        }
        super.nodeStructureChanged(node)
    }

    override fun removeNodeFromParent(node: MutableTreeNode?) {
        if (node is SimpleTreeNode<*>) {
            for (e in node.getAllChildren()) {
                hostManager.removeHost(e.id)
            }
            hostManager.removeHost(node.id)
        }
        super.removeNodeFromParent(node)
    }

    private fun registerDynamicExtensions() {
        // 底层数据变动刷新
        DynamicExtensionHandler.getInstance()
            .register(DatabaseChangedExtension::class.java, MyDatabaseChangedExtension())
            .let { Disposer.register(this, it) }

        // 用户信息变更
        DynamicExtensionHandler.getInstance()
            .register(AccountExtension::class.java, MyAccountAccountExtension())
            .let { Disposer.register(this, it) }

        // 服务器签名发生变更
        DynamicExtensionHandler.getInstance()
            .register(ServerSignedExtension::class.java, object : ServerSignedExtension {
                override fun onSignedChanged(oldSigned: Boolean, newSigned: Boolean) {
                    reload(getRoot())
                }
            }).let { Disposer.register(this, it) }
    }

    private inner class MyDatabaseChangedExtension : DatabaseChangedExtension {
        override fun onDataChanged(
            id: String,
            type: String,
            action: DatabaseChangedExtension.Action,
            source: DatabaseChangedExtension.Source
        ) {

            if (id.isBlank() || source != DatabaseChangedExtension.Source.Sync) return
            if (type.isNotBlank() && type != DataType.Host.name) return

            if (action == DatabaseChangedExtension.Action.Added) {
                val host = hostManager.getHost(id) ?: return
                for (node in getRoot().getAllChildren()) {
                    if (node.id == host.parentId) {
                        insertNodeInto(
                            HostTreeNode(host),
                            node,
                            if (host.isFolder) node.folderCount else node.childCount,
                            false
                        )
                        // 因为有可能子项先落库，文件夹后落库，所以这里刷新一下
                        if (node.isFolder) reload(node)
                        return
                    }
                }
            } else if (action == DatabaseChangedExtension.Action.Removed) {
                for (node in getRoot().getAllChildren()) {
                    if (node.id == id) {
                        removeNodeFromParent(node)
                        return
                    }
                }
            } else if (action == DatabaseChangedExtension.Action.Changed) {
                for (node in getRoot().getAllChildren()) {
                    if (node.id == id) {
                        reload(node.parent ?: break)
                        break
                    }
                }
            }
        }
    }

    private inner class MyAccountAccountExtension : AccountExtension {
        override fun onAccountChanged(oldAccount: Account, newAccount: Account) {
            if (oldAccount.id != newAccount.id) reload(getRoot())
        }
    }


}