package app.termora.transfer

import java.nio.file.FileSystem
import java.nio.file.Path

class DefaultTransportSupport(private val fileSystem: FileSystem, private val defaultPath: Path) : TransportSupport {
    override fun getFileSystem(): FileSystem {
        return fileSystem
    }

    override fun getDefaultPath(): Path {
        return defaultPath
    }
}