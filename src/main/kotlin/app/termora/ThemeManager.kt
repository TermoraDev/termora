package app.termora

import app.termora.database.DatabaseManager
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionManager
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.FlatAnimatedLafChange
import com.jthemedetecor.OsThemeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.function.Consumer
import javax.swing.PopupFactory
import javax.swing.SwingUtilities
import javax.swing.UIManager

interface ThemeChangeExtension : Extension {
    fun onChanged()
}

internal class ThemeManager private constructor() {


    companion object {
        private val log = LoggerFactory.getLogger(ThemeManager::class.java)
        fun getInstance(): ThemeManager {
            return ApplicationScope.forApplicationScope().getOrCreate(ThemeManager::class) { ThemeManager() }
        }
    }

    val appearance by lazy { DatabaseManager.getInstance().appearance }
    val themes = mapOf(
        "Light" to LightLaf::class.java.name,
        "Dark" to DarkLaf::class.java.name,
        "Dracula" to DraculaLaf::class.java.name,
        "iTerm2 Dark" to iTerm2DarkLaf::class.java.name,
        "Termius Dark" to TermiusDarkLaf::class.java.name,
        "Termius Light" to TermiusLightLaf::class.java.name,
        "Atom One Dark" to AtomOneDarkLaf::class.java.name,
        "Atom One Light" to AtomOneLightLaf::class.java.name,
        "Everforest Dark" to EverforestDarkLaf::class.java.name,
        "Everforest Light" to EverforestLightLaf::class.java.name,
        "Octocat Dark" to OctocatDarkLaf::class.java.name,
        "Octocat Light" to OctocatLightLaf::class.java.name,
        "Night Owl" to NightOwlLaf::class.java.name,
        "Light Owl" to LightOwlLaf::class.java.name,
        "Nord Dark" to NordDarkLaf::class.java.name,
        "Nord Light" to NordLightLaf::class.java.name,
        "GitHub Dark" to GitHubDarkLaf::class.java.name,
        "GitHub Light" to GitHubLightLaf::class.java.name,
        "Novel" to NovelLaf::class.java.name,
        "Aura" to AuraLaf::class.java.name,
        "Cobalt2" to Cobalt2Laf::class.java.name,
        "Ayu Dark" to AyuDarkLaf::class.java.name,
        "Ayu Light" to AyuLightLaf::class.java.name,
        "Homebrew" to HomebrewLaf::class.java.name,
        "Pro" to ProLaf::class.java.name,
        "Chalk" to ChalkLaf::class.java.name,
    )


    /**
     * 当前的主题
     */
    val theme: String
        get() {
            val themeClass = UIManager.getLookAndFeel().javaClass.name
            for (e in themes.entries) {
                if (e.value == themeClass) {
                    return e.key
                }
            }
            return themeClass
        }


    init {
        swingCoroutineScope.launch(Dispatchers.IO) {
            OsThemeDetector.getDetector().registerListener(object : Consumer<Boolean> {
                override fun accept(isDark: Boolean) {
                    if (!appearance.followSystem) {
                        return
                    }

                    SwingUtilities.invokeLater {
                        if (isDark) {
                            change(appearance.darkTheme)
                        } else {
                            change(appearance.lightTheme)
                        }
                    }
                }
            })
        }
    }


    fun change(classname: String, immediate: Boolean = false) {
        val themeClassname = themes.getOrDefault(classname, classname)

        if (UIManager.getLookAndFeel().javaClass.name == themeClassname) {
            return
        }


        if (immediate) {
            immediateChange(themeClassname)
        } else {
            FlatAnimatedLafChange.showSnapshot()
            immediateChange(themeClassname)
            FlatLaf.updateUI()
            FlatAnimatedLafChange.hideSnapshotWithAnimation()
        }

        ExtensionManager.getInstance().getExtensions(ThemeChangeExtension::class.java)
            .forEach { it.onChanged() }
    }

    private fun immediateChange(classname: String) {
        try {

            val oldPopupFactory = PopupFactory.getSharedInstance()
            UIManager.setLookAndFeel(classname)
            PopupFactory.setSharedInstance(oldPopupFactory)

        } catch (ex: Exception) {
            log.error(ex.message, ex)
        }
    }


}