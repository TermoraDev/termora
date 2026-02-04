package app.termora.plugin.internal.windows

import app.termora.*
import app.termora.OptionsPane.Option
import com.formdev.flatlaf.util.SystemInfo
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.*

class WindowsSettingsOption : JPanel(BorderLayout()), Option {
    companion object {
        private val log = LoggerFactory.getLogger(WindowsSettingsOption::class.java)
        private const val REG_KEY_BG = "HKCU\\Software\\Classes\\Directory\\Background\\shell\\Termora"
        private const val REG_KEY_DIR = "HKCU\\Software\\Classes\\Directory\\shell\\Termora"
    }

    private val addToContextMenuCheckBox = JCheckBox(I18n.getString("termora.settings.windows.add-to-context-menu"))
    private val extendedCheckBox = JCheckBox(I18n.getString("termora.settings.windows.extended"))
    private var isInitialized = false

    override fun getTitle(): String {
        return I18n.getString("termora.settings.windows")
    }

    override fun getIcon(isSelected: Boolean): Icon {
        return if (isSelected) Icons.microsoftWindows.dark else Icons.microsoftWindows
    }

    override fun getJComponent(): JComponent {
        return this
    }

    override fun onSelected() {
        if (isInitialized) return
        initView()
        initEvents()
        isInitialized = true

        // Initial check in background
        swingCoroutineScope.launch {
            checkRegistryStatus()
        }
    }

    private fun initView() {
        val layout = FormLayout(
            "left:pref, \$FORM_MARGIN, default:grow",
            "pref, \$FORM_MARGIN, pref"
        )

        val builder = FormBuilder.create().layout(layout).debug(false)
        builder.add(addToContextMenuCheckBox).xy(1, 1)
        builder.add(extendedCheckBox).xy(1, 3)

        add(builder.build(), BorderLayout.CENTER)

        // Disable initially until checked
        addToContextMenuCheckBox.isEnabled = false
        extendedCheckBox.isEnabled = false
    }

    private fun initEvents() {
        addToContextMenuCheckBox.addItemListener { e ->
            // If it was programmatic selection (from checkRegistryStatus), ignore
            if (!addToContextMenuCheckBox.isEnabled) return@addItemListener

            if (e.stateChange == ItemEvent.SELECTED) {
                swingCoroutineScope.launch {
                    val success = register(extendedCheckBox.isSelected)
                    if (!success) {
                        // Revert check without triggering event (requires disabling logic again? or just removing listener temporarily)
                        // But here we are on EDT (swingCoroutineScope resumes on EDT)
                        temporarilyDisableListeners {
                            addToContextMenuCheckBox.isSelected = false
                        }
                        JOptionPane.showMessageDialog(this@WindowsSettingsOption, I18n.getString("termora.settings.windows.register-failed"))
                    } else {
                        extendedCheckBox.isEnabled = true
                    }
                }
            } else {
                swingCoroutineScope.launch {
                    val success = unregister()
                    if (!success) {
                        temporarilyDisableListeners {
                            addToContextMenuCheckBox.isSelected = true
                        }
                        JOptionPane.showMessageDialog(this@WindowsSettingsOption, I18n.getString("termora.settings.windows.unregister-failed"))
                    } else {
                        extendedCheckBox.isEnabled = false
                    }
                }
            }
        }

        extendedCheckBox.addItemListener { e ->
            if (addToContextMenuCheckBox.isSelected && addToContextMenuCheckBox.isEnabled) {
                 swingCoroutineScope.launch {
                     register(extendedCheckBox.isSelected)
                 }
            }
        }
    }

    private inline fun temporarilyDisableListeners(block: () -> Unit) {
        val listeners = addToContextMenuCheckBox.itemListeners
        listeners.forEach { addToContextMenuCheckBox.removeItemListener(it) }
        try {
            block()
        } finally {
            listeners.forEach { addToContextMenuCheckBox.addItemListener(it) }
        }
    }

    private suspend fun checkRegistryStatus() {
        if (!SystemInfo.isWindows) {
            return
        }

        val result = withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder("reg", "query", REG_KEY_BG).start()
                val exists = process.waitFor() == 0

                var isExtended = false
                if (exists) {
                    val processExt = ProcessBuilder("reg", "query", REG_KEY_BG, "/v", "Extended").start()
                    isExtended = processExt.waitFor() == 0
                }
                exists to isExtended
            } catch (e: Exception) {
                log.error("Failed to check registry status", e)
                false to false
            }
        }

        val exists = result.first
        val isExtended = result.second

        temporarilyDisableListeners {
            addToContextMenuCheckBox.isSelected = exists
            extendedCheckBox.isSelected = isExtended

            addToContextMenuCheckBox.isEnabled = true
            extendedCheckBox.isEnabled = exists
        }

        // Extended checkbox listener also needs to be handled?
        // We set isSelected inside temporarilyDisableListeners only for addToContextMenuCheckBox.
        // But extendedCheckBox doesn't trigger register/unregister unless user clicks it.
    }

    private suspend fun register(extended: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            val appPath = Application.getAppPath()
             if (appPath.isBlank()) {
                 return@withContext false
            }
            val exePath = appPath
            val command = "\"$exePath\" \"%V\""

            try {
                addRegistryKey(REG_KEY_BG, "Open Termora here", exePath, command, extended)
                addRegistryKey(REG_KEY_DIR, "Open Termora here", exePath, command, extended)
                true
            } catch (e: Exception) {
                log.error("Failed to register context menu", e)
                false
            }
        }
    }


    private suspend fun unregister(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                deleteRegistryKey(REG_KEY_BG)
                deleteRegistryKey(REG_KEY_DIR)
                true
            } catch (e: Exception) {
                 log.error("Failed to unregister context menu", e)
                 false
            }
        }
    }

    private fun addRegistryKey(key: String, name: String, icon: String, command: String, extended: Boolean) {
        runReg("add", key, "/ve", "/d", name, "/f")
        runReg("add", key, "/v", "Icon", "/d", icon, "/f")
        runReg("add", "$key\\command", "/ve", "/d", command, "/f")

        if (extended) {
            runReg("add", key, "/v", "Extended", "/f")
        } else {
            runReg("delete", key, "/v", "Extended", "/f")
        }
    }

    private fun deleteRegistryKey(key: String) {
        runReg("delete", key, "/f")
    }

    private fun runReg(vararg args: String) {
        val pb = ProcessBuilder("reg", *args)
        pb.redirectErrorStream(true)
        val p = pb.start()
        val exit = p.waitFor()
        if (exit != 0 && exit != 1) {
             val output = String(p.inputStream.readAllBytes())
             if (args.contains("add")) {
                 throw RuntimeException("Reg command failed: ${args.joinToString(" ")}\nOutput: $output")
             }
        }
    }
}
