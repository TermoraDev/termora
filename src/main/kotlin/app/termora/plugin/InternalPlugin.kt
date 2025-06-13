package app.termora.plugin

internal abstract class InternalPlugin() : Plugin {
    protected val support = ExtensionSupport()

    override fun getAuthor(): String {
        return "TermoraDev"
    }



}