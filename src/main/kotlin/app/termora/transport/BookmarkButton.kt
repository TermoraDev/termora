package app.termora.transport

import app.termora.Application.ohMyJson
import app.termora.DynamicColor
import app.termora.I18n
import app.termora.Icons
import app.termora.assertEventDispatchThread
import app.termora.db.Database
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.ui.FlatUIUtils
import kotlinx.serialization.encodeToString
import org.apache.commons.lang3.StringUtils
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

class BookmarkButton : JButton(Icons.bookmarks) {
    private val properties by lazy { Database.instance.properties }
    private val arrowWidth = 16
    private val arrowSize = 6

    /**
     * 为 true 表示在书签内
     */
    var isBookmark = false
        set(value) {
            field = value
            icon = if (value) {
                Icons.bookmarksOff
            } else {
                Icons.bookmarks
            }
        }


    init {
        val oldWidth = preferredSize.width

        preferredSize = Dimension(oldWidth + arrowWidth, preferredSize.height)
        horizontalAlignment = SwingConstants.LEFT


        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (e.x < oldWidth) {
                        super@BookmarkButton.fireActionPerformed(
                            ActionEvent(
                                this@BookmarkButton,
                                ActionEvent.ACTION_PERFORMED,
                                StringUtils.EMPTY
                            )
                        )
                    } else {
                        showBookmarks(e)
                    }
                }
            }
        })

        isBookmark = false
    }

    private fun showBookmarks(e: MouseEvent) {
        if (StringUtils.isBlank(name)) return

        val popupMenu = FlatPopupMenu()
        val bookmarks = getBookmarks()
        popupMenu.add(I18n.getString("termora.transport.bookmarks")).addActionListener {
            val list = BookmarksDialog(SwingUtilities.getWindowAncestor(this), bookmarks).open()
            properties.putString(name, ohMyJson.encodeToString(list))
        }

        if (bookmarks.isNotEmpty()) {
            popupMenu.addSeparator()
            for (bookmark in bookmarks) {
                popupMenu.add(bookmark).addActionListener {
                    super@BookmarkButton.fireActionPerformed(
                        ActionEvent(
                            this@BookmarkButton,
                            ActionEvent.ACTION_PERFORMED,
                            bookmark
                        )
                    )
                }
            }
        }



        popupMenu.show(e.component, -(popupMenu.preferredSize.width / 2 - width / 2), height + 2)
    }

    fun addBookmark(text: String) {
        assertEventDispatchThread()
        if (StringUtils.isBlank(name)) return
        val bookmarks = getBookmarks().toMutableList()
        bookmarks.add(text)
        properties.putString(name, ohMyJson.encodeToString(bookmarks))
    }

    fun deleteBookmark(text: String) {
        assertEventDispatchThread()
        if (StringUtils.isBlank(name)) return
        val bookmarks = getBookmarks().toMutableList()
        bookmarks.removeIf { text == it }
        properties.putString(name, ohMyJson.encodeToString(bookmarks))
    }

    fun getBookmarks(): List<String> {
        if (StringUtils.isBlank(name)) {
            return emptyList()
        }


        val text = properties.getString(name, "[]")
        if (StringUtils.isNotBlank(text)) {
            runCatching { ohMyJson.decodeFromString<List<String>>(text) }.onSuccess {
                return it
            }
        }


        return emptyList()
    }

    override fun paintComponent(g: Graphics) {
        val g2d = g as Graphics2D
        super.paintComponent(g2d)

        val x = preferredSize.width - arrowWidth

        g.color = DynamicColor.BorderColor
        g.drawLine(x + 1, 4, x + 1, preferredSize.height - 2)

        g.color = if (FlatLaf.isLafDark()) Color(206, 208, 214) else Color(108, 112, 126)

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        FlatUIUtils.paintArrow(
            g2d, x, preferredSize.height / 2 - arrowSize, arrowWidth, arrowWidth, SwingConstants.SOUTH,
            false, arrowSize, 0f, 0f, 0f
        )


    }

    override fun isSelected(): Boolean {
        return false
    }

    /**
     * 忽略默认的触发事件
     */
    override fun fireActionPerformed(event: ActionEvent) {

    }


}