package app.termora.transfer

import java.nio.file.FileSystem
import java.nio.file.Path


internal interface TransportSupport {
    fun getFileSystem(): FileSystem
    fun getDefaultPath(): Path
}