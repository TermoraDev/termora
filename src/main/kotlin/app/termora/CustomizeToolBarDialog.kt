package app.termora

import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.action.ActionManager
import java.awt.Component
import java.awt.Dimension
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import kotlin.math.max
import kotlin.math.min

internal class CustomizeToolBarDialog(
    owner: Window,
    private val windowScope: WindowScope,
    private val model: TermoraToolbarModel,
) : DialogWrapper(owner) {

    private val moveTopBtn = JButton(Icons.moveUp)
    private val moveBottomBtn = JButton(Icons.moveDown)
    private val upBtn = JButton(Icons.up)
    private val downBtn = JButton(Icons.down)

    private val leftBtn = JButton(Icons.left)
    private val rightBtn = JButton(Icons.right)
    private val resetBtn = JButton(Icons.refresh)
    private val allToLeftBtn = JButton(Icons.applyNotConflictsRight)
    private val allToRightBtn = JButton(Icons.applyNotConflictsLeft)

    private val leftList = ToolBarActionList()
    private val rightList = ToolBarActionList()
    private val actionManager get() = ActionManager.getInstance()

    private var isOk = false
    private val actions = mutableListOf<ToolBarAction>()

    init {
        size = Dimension(UIManager.getInt("Dialog.width") - 150, UIManager.getInt("Dialog.height") - 100)
        isModal = true
        controlsVisible = false
        isResizable = false
        title = I18n.getString("termora.toolbar.customize-toolbar")
        setLocationRelativeTo(null)

        moveTopBtn.isEnabled = false
        moveBottomBtn.isEnabled = false
        downBtn.isEnabled = false
        upBtn.isEnabled = false

        leftBtn.isEnabled = false
        rightBtn.isEnabled = false

        initEvents()

        init()
    }


    override fun createCenterPanel(): JComponent {

        allToLeftBtn.isEnabled = !rightList.model.isEmpty
        allToRightBtn.isEnabled = !leftList.model.isEmpty

        val box = JToolBar(JToolBar.VERTICAL)
        box.add(Box.createVerticalStrut(leftList.fixedCellHeight))
        box.add(rightBtn)
        box.add(leftBtn)
        box.add(Box.createVerticalGlue())
        box.add(resetBtn)
        box.add(Box.createVerticalGlue())
        box.add(allToRightBtn)
        box.add(allToLeftBtn)
        box.add(Box.createVerticalStrut(leftList.fixedCellHeight))

        val box2 = JToolBar(JToolBar.VERTICAL)
        box2.add(Box.createVerticalStrut(leftList.fixedCellHeight))
        box2.add(moveTopBtn)
        box2.add(upBtn)
        box2.add(Box.createVerticalGlue())
        box2.add(downBtn)
        box2.add(moveBottomBtn)
        box2.add(Box.createVerticalStrut(leftList.fixedCellHeight))


        return FormBuilder.create().debug(false)
            .border(BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor))
            .layout(FormLayout("default:grow, pref, default:grow, pref", "fill:p:grow"))
            .add(JScrollPane(leftList).apply {
                border = BorderFactory.createMatteBorder(0, 0, 0, 1, DynamicColor.BorderColor)
            }).xy(1, 1)
            .add(box).xy(2, 1)
            .add(JScrollPane(rightList).apply {
                border = BorderFactory.createMatteBorder(0, 1, 0, 1, DynamicColor.BorderColor)
            }).xy(3, 1)
            .add(box2).xy(4, 1)
            .build()
    }

    private fun initEvents() {

        rightList.addListSelectionListener { resetMoveButtons() }

        leftList.addListSelectionListener {
            val indices = leftList.selectedIndices
            rightBtn.isEnabled = indices.isNotEmpty()
        }

        leftList.model.addListDataListener(object : ListDataListener {
            override fun intervalAdded(e: ListDataEvent) {
                contentsChanged(e)
            }

            override fun intervalRemoved(e: ListDataEvent) {
                contentsChanged(e)
            }

            override fun contentsChanged(e: ListDataEvent) {
                allToLeftBtn.isEnabled = !rightList.model.isEmpty
                allToRightBtn.isEnabled = !leftList.model.isEmpty
                resetMoveButtons()
            }
        })

        rightList.model.addListDataListener(object : ListDataListener {
            override fun intervalAdded(e: ListDataEvent) {
                contentsChanged(e)
            }

            override fun intervalRemoved(e: ListDataEvent) {
                contentsChanged(e)
            }

            override fun contentsChanged(e: ListDataEvent) {
                allToLeftBtn.isEnabled = !rightList.model.isEmpty
                allToRightBtn.isEnabled = !leftList.model.isEmpty
                resetMoveButtons()
            }
        })

        resetBtn.addActionListener {
            leftList.model.removeAllElements()
            rightList.model.removeAllElements()
            for (action in model.getAllActions()) {
                getActionHolder(action.id)?.let { rightList.model.addElement(it) }
            }
        }

        // move first
        moveTopBtn.addActionListener {
            val indices = rightList.selectedIndices.sortedDescending()
            rightList.clearSelection()
            for (index in indices.indices) {
                val ele = rightList.model.getElementAt(indices[index])
                rightList.model.removeElementAt(indices[index])
                rightList.model.add(index, ele)
                rightList.selectionModel.addSelectionInterval(index, max(index - 1, 0))
            }
        }

        // move up
        upBtn.addActionListener {
            val indices = rightList.selectedIndices.sortedDescending()
            rightList.clearSelection()
            for (index in indices) {
                val ele = rightList.model.getElementAt(index)
                rightList.model.removeElementAt(index)
                rightList.model.add(index - 1, ele)
                rightList.selectionModel.addSelectionInterval(max(index - 1, 0), max(index - 1, 0))
            }
        }

        // move down
        downBtn.addActionListener {
            val indices = rightList.selectedIndices.sortedDescending()
            rightList.clearSelection()
            for (index in indices) {
                val ele = rightList.model.getElementAt(index)
                rightList.model.removeElementAt(index)
                rightList.model.add(index + 1, ele)
                rightList.selectionModel.addSelectionInterval(index + 1, index + 1)
            }
        }

        // move last
        moveBottomBtn.addActionListener {
            val indices = rightList.selectedIndices.sortedDescending()
            val size = rightList.model.size
            rightList.clearSelection()
            for (index in indices.indices) {
                val ele = rightList.model.getElementAt(indices[index])
                rightList.model.removeElementAt(indices[index])
                rightList.model.add(size - index - 1, ele)
                rightList.selectionModel.addSelectionInterval(size - index - 1, size - index - 1)
            }
        }

        allToLeftBtn.addActionListener {
            while (!rightList.model.isEmpty) {
                val ele = rightList.model.getElementAt(0)
                rightList.model.removeElementAt(0)
                leftList.model.addElement(ele)
            }
        }

        allToRightBtn.addActionListener {
            while (!leftList.model.isEmpty) {
                val ele = leftList.model.getElementAt(0)
                leftList.model.removeElementAt(0)
                rightList.model.addElement(ele)
            }
        }

        leftBtn.addActionListener {
            val indices = rightList.selectedIndices.sortedDescending()
            for (index in indices) {
                val ele = rightList.model.getElementAt(index)
                rightList.model.removeElementAt(index)
                leftList.model.addElement(ele)
            }
            rightList.clearSelection()
            val index = min(indices.max(), rightList.model.size - 1)
            if (!rightList.model.isEmpty) {
                rightList.addSelectionInterval(index, index)
            }
        }

        rightBtn.addActionListener {
            val indices = leftList.selectedIndices.sortedDescending()
            val rightSelectedIndex = if (rightList.selectedIndices.isEmpty()) rightList.model.size else
                rightList.selectionModel.maxSelectionIndex + 1

            if (indices.isNotEmpty()) {
                for (index in indices.indices) {
                    val ele = leftList.model.getElementAt(indices[index])
                    leftList.model.removeElementAt(indices[index])
                    rightList.model.add(rightSelectedIndex + index, ele)
                }

                leftList.clearSelection()
                val index = min(indices.max(), leftList.model.size - 1)
                if (!leftList.model.isEmpty) {
                    leftList.addSelectionInterval(index, index)
                }

                rightList.clearSelection()
                rightList.addSelectionInterval(rightSelectedIndex, rightSelectedIndex)
            }
        }

        addWindowListener(object : WindowAdapter() {
            override fun windowOpened(e: WindowEvent) {
                removeWindowListener(this)

                for (action in model.getActions()) {
                    if (action.visible) {
                        getActionHolder(action.id)?.let { rightList.model.addElement(it) }
                    } else {
                        getActionHolder(action.id)?.let { leftList.model.addElement(it) }
                    }
                }

            }
        })
    }

    private fun getActionHolder(actionId: String): ActionHolder? {
        val action = actionManager.getAction(actionId)
        if (action == null) return null
        return ActionHolder(actionId, action)
    }

    private fun resetMoveButtons() {
        val indices = rightList.selectedIndices
        if (indices.isEmpty()) {
            moveTopBtn.isEnabled = false
            moveBottomBtn.isEnabled = false
            downBtn.isEnabled = false
            upBtn.isEnabled = false
        } else {
            moveTopBtn.isEnabled = !indices.contains(0)
            upBtn.isEnabled = moveTopBtn.isEnabled
            moveBottomBtn.isEnabled = !indices.contains(rightList.model.size - 1)
            downBtn.isEnabled = moveBottomBtn.isEnabled
        }
        leftBtn.isEnabled = indices.isNotEmpty()
    }

    private class ToolBarActionList : JList<ActionHolder>() {
        private val model = DefaultListModel<ActionHolder>()

        init {
            initView()
            initEvents()
            setModel(model)
        }

        private fun initView() {
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            background = UIManager.getColor("window")
            fixedCellHeight = UIManager.getInt("Tree.rowHeight")
            cellRenderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    var text = value?.toString() ?: StringUtils.EMPTY
                    if (value is ActionHolder) {
                        val action = value.action
                        text = action.getValue(Action.NAME)?.toString() ?: text
                    }

                    val c = super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
                    if (value is ActionHolder) {
                        val action = value.action
                        val icon = action.getValue(Action.SMALL_ICON) as Icon?
                        if (icon != null) {
                            this.icon = icon
                            if (icon is DynamicIcon) {
                                if (isSelected && cellHasFocus) {
                                    this.icon = icon.dark
                                }
                            }
                        }
                    }

                    return c
                }
            }

        }

        private fun initEvents() {

        }

        override fun getModel(): DefaultListModel<ActionHolder> {
            return model
        }
    }

    override fun doOKAction() {
        isOk = true

        val actions = mutableListOf<ToolBarAction>()
        for (i in 0 until rightList.model.size()) {
            actions.add(ToolBarAction(rightList.model.getElementAt(i).id, true))
        }

        for (i in 0 until leftList.model.size()) {
            actions.add(ToolBarAction(leftList.model.getElementAt(i).id, false))
        }

        this.actions.clear()
        this.actions.addAll(actions)

        super.doOKAction()
    }

    fun getActions()=actions

    fun open(): Boolean {
        isModal = true
        isVisible = true
        return isOk
    }

    private class ActionHolder(val id: String, val action: Action)
}