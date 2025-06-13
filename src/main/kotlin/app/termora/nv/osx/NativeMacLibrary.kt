package app.termora.nv.osx

import de.jangassen.jfa.foundation.Foundation
import de.jangassen.jfa.foundation.ID
import org.slf4j.LoggerFactory
import java.awt.Component
import java.awt.Window

object NativeMacLibrary {
    private val log = LoggerFactory.getLogger(NativeMacLibrary::class.java)

    fun getNSWindow(window: Window): Long? {
        try {
            val peerField = Component::class.java.getDeclaredField("peer") ?: return null
            peerField.isAccessible = true
            val peer = peerField.get(window) ?: return null

            val platformWindowField = peer.javaClass.getDeclaredField("platformWindow") ?: return null
            platformWindowField.isAccessible = true
            val platformWindow = platformWindowField.get(peer)

            val ptrField = Class.forName("sun.lwawt.macosx.CFRetainedResource")
                .getDeclaredField("ptr") ?: return null
            ptrField.isAccessible = true
            return ptrField.get(platformWindow) as Long
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
            return null
        }
    }

    fun setControlsVisible(window: Window, visible: Boolean) {
        val nsWindow = ID(getNSWindow(window) ?: return)
        try {
            Foundation.executeOnMainThread(true, true) {
                for (i in 0..2) {
                    val button = Foundation.invoke(nsWindow, "standardWindowButton:", i)
                    Foundation.invoke(button, "setHidden:", !visible)
                }
            }
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
        }
    }


}