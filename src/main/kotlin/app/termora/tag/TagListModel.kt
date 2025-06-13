package app.termora.tag

import app.termora.account.AccountOwner
import javax.swing.DefaultListModel

class TagListModel(private val accountOwner: AccountOwner) : DefaultListModel<Tag>() {
    private val tagManager get() = TagManager.getInstance()

    init {
        super.addAll(tagManager.getTags(accountOwner.id))
    }

    override fun addElement(element: Tag) {
        tagManager.addTag(element, accountOwner)
        super.addElement(element)
    }

    override fun setElementAt(element: Tag, index: Int) {
        tagManager.addTag(element, accountOwner)
        super.setElementAt(element, index)
    }

    override fun removeElement(obj: Any): Boolean {
        if (obj is Tag) {
            tagManager.removeTag(obj.id)
        }
        return super.removeElement(obj)
    }

    override fun removeElementAt(index: Int) {
        removeElement(getElementAt(index))
    }
}