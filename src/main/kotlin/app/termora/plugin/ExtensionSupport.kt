package app.termora.plugin

class ExtensionSupport {
    private val extensions = mutableMapOf<Class<out Extension>, MutableList<ExtensionLazy>>()


    fun <T : Extension> addExtension(clazz: Class<T>, supplier: () -> T) {
        extensions.computeIfAbsent(clazz) { mutableListOf() }.add(ExtensionLazy(supplier))
    }

    fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        val lazies = extensions[clazz] ?: return emptyList()
        val extensions = mutableListOf<T>()
        for (lazy in lazies) {
            extensions.add(clazz.cast(lazy.data))
        }
        return extensions
    }


    private class ExtensionLazy(private val supplier: () -> Extension) {
        val data by lazy { supplier.invoke() }
    }

}