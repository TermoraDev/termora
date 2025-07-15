package app.termora.plugin

import app.termora.ApplicationScope
import org.slf4j.LoggerFactory
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

class ExtensionManager private constructor() {
    companion object {
        private val log = LoggerFactory.getLogger(ExtensionManager::class.java)
        fun getInstance(): ExtensionManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(ExtensionManager::class) { ExtensionManager() }
        }
    }

    private val map = ConcurrentHashMap<Extension, Any>()

    /**
     * @return 不要缓存结果，因为可能会有动态扩展
     */
    fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        val extensions = mutableListOf<T>()

        for (plugin in PluginManager.getInstance().getLoadedPlugins()) {
            try {
                for (extension in plugin.getExtensions(clazz)) {
                    if (clazz.isInstance(extension)) {
                        val proxy = map.computeIfAbsent(extension) { ExtensionProxy(plugin, extension).proxy }
                        extensions.add(clazz.cast(proxy))
                    }
                }
            } catch (e: Throwable) {
                if (log.isErrorEnabled) {
                    log.error("Plugin {} getExtensions: {}", plugin.getName(), e.message, e)
                }
            }
        }

        return extensions.sortedBy { it.ordered() }
    }

    fun isExtension(extension: Extension, clazz: KClass<*>): Boolean {
        if (clazz.isInstance(extension)) {
            return true
        }

        if (Proxy.isProxyClass(extension.javaClass)) {
            val invocationHandler = Proxy.getInvocationHandler(extension)
            if (invocationHandler is ExtensionProxy) {
                return clazz.isInstance(invocationHandler.extension())
            }
        }

        return false
    }
}