package app.termora

enum class AppLayout {
    /**
     * Windows
     */
    Zip,
    Exe,
    Appx,

    /**
     * macOS
     */
    App,

    /**
     * Linux
     */
    TarGz,
    AppImage,
    Deb,
}