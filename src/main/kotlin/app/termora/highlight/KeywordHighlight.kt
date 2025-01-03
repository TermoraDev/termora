package app.termora.highlight

import app.termora.toSimpleString
import kotlinx.serialization.Serializable
import org.apache.commons.lang3.StringUtils
import java.util.*

@Serializable
data class KeywordHighlight(
    val id: String = UUID.randomUUID().toSimpleString(),

    /**
     * 关键词
     */
    val keyword: String = StringUtils.EMPTY,

    /**
     * 描述
     */
    val description: String = StringUtils.EMPTY,

    /**
     * [keyword] 是否大小写匹配，如果为 true 表示不忽略大小写，也就是：'A != a'；如果为 false 那么 'A == a'
     */
    val matchCase: Boolean = false,

    /**
     * 0 是取前景色
     */
    val textColor: Int = 0,

    /**
     * 0 是取背景色
     */
    val backgroundColor: Int = 0,

    /**
     * 是否加粗
     */
    val bold: Boolean = false,

    /**
     * 是否斜体
     */
    val italic: Boolean = false,

    /**
     * 删除线
     */
    val lineThrough: Boolean = false,

    /**
     * 下划线
     */
    val underline: Boolean = false,

    /**
     * 是否启用
     */
    val enabled: Boolean = true,

    /**
     * 排序
     */
    val sort: Long = System.currentTimeMillis()
)