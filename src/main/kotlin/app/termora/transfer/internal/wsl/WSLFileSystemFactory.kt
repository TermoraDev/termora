package app.termora.transfer.internal.wsl

import java.nio.file.FileSystem


class WSLFileSystemFactory {
    companion object {
        val instance by lazy { WSLFileSystemFactory() }
    }

    fun createWSLFileSystem(): FileSystem {
        return WSLFileSystem()
    }
}