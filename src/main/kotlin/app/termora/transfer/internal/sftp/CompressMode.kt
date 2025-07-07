package app.termora.transfer.internal.sftp

internal enum class CompressMode(val extension: String) {
    TarGz("tar.gz"),
    Tar("tar"),
    Zip("zip"),
    SevenZ("7z"),
}