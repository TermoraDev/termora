package app.termora.vfs2

import app.termora.DynamicIcon

/**
 * 文件描述
 */
interface FileObjectDescriptor {

    /**
     * 图标
     */
    fun getIcon(width: Int, height: Int): DynamicIcon? = null

    /**
     * 获取类型描述
     */
    fun getTypeDescription(): String? = null

    /**
     * 最后修改时间，时间戳
     */
    fun getLastModified(): Long? = null

    /**
     * 获取所有者
     */
    fun getOwner(): String? = null
}