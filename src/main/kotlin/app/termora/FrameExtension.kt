package app.termora

import app.termora.plugin.Extension

interface FrameExtension : Extension {
    /**
     * 自定义
     */
    fun customize(frame: TermoraFrame)
}