package app.termora.plugin.internal.extension

import app.termora.ApplicationScope
import app.termora.Disposable
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionManager
import org.apache.commons.lang3.ArrayUtils

internal class DynamicExtensionHandler private constructor() {

    companion object {
        fun getInstance(): DynamicExtensionHandler {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(DynamicExtensionHandler::class) { DynamicExtensionHandler() }
        }
    }

    private val extensionManager get() = ExtensionManager.getInstance()
    private var extensions = emptyArray<Any>()

    fun <T : Extension> register(clazz: Class<T>, extension: T): Disposable {
        synchronized(extensions) {
            extensions += clazz
            extensions += extension
        }

        return object : Disposable {
            override fun dispose() {
                unregister(extension)
            }
        }
    }

    fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        val list = mutableListOf<T>()
        synchronized(extensions) {
            for (i in 0 until extensions.size) {
                if (extensions[i] == clazz) {
                    val extension = extensions[i + 1]
                    if (extension is Extension) {
                        if (extensionManager.isExtension(extension, clazz.kotlin)) {
                            list.add(clazz.cast(extension))
                        }
                    }
                }
            }
        }
        return list
    }

    fun <T : Extension> unregister(extension: T) {
        synchronized(extensions) {
            var i = 0
            while (i < extensions.size) {
                if (extensions[i] is Extension && extensions[i] == extension) {
                    // clazz
                    extensions = ArrayUtils.remove(extensions, i - 1)
                    // extension
                    extensions = ArrayUtils.remove(extensions, i - 1)
                    // 从头开始再删
                    i = 0
                    continue
                }
                i++
            }
        }
    }


}