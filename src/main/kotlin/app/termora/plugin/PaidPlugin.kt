package app.termora.plugin

interface PaidPlugin : Plugin {
    override fun isPaid() = true
}