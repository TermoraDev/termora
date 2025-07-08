package app.termora.plugin.internal.badge

import app.termora.*
import java.awt.Color
import java.util.*
import javax.swing.JComponent
import javax.swing.UIManager

class Badge private constructor(scope: Scope) {
    companion object {
        fun getInstance(scope: WindowScope): Badge {
            return scope.getOrCreate(Badge::class) { Badge(scope) }
        }

        fun getInstance(): Badge {
            val scope = ApplicationScope.forApplicationScope()
            return scope.getOrCreate(Badge::class) { Badge(scope) }
        }
    }

    init {
        Disposer.register(scope, object : Disposable {
            override fun dispose() {
                map.clear()
            }
        })
    }

    private val map = WeakHashMap<JComponent, BadgePresentation>()

    fun addBadge(component: JComponent): BadgePresentation {
        val presentation = object : BadgePresentation {
            override var visible: Boolean = true
            override var color: Color = UIManager.getColor("Component.error.focusedBorderColor")

            override fun dispose() {
                removeBadge(component)
            }
        }
        map[component] = presentation
        return presentation
    }

    fun removeBadge(component: JComponent) {
        map.remove(component)
    }

    fun getBadges(): Map<JComponent, BadgePresentation> {
        return map.toMap()
    }


}