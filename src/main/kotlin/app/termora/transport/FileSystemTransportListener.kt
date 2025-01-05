package app.termora.transport

import java.nio.file.Path
import java.util.*

interface FileSystemTransportListener : EventListener {
    /**
     * @param workdir 当前工作目录
     * @param isDirectory 要传输的是否是文件夹
     * @param path 要传输的文件/文件夹
     */
    fun transport(fileSystemPanel: FileSystemPanel, workdir: Path, isDirectory: Boolean, path: Path)


    interface Provider {
        fun addFileSystemTransportListener(listener: FileSystemTransportListener)
        fun removeFileSystemTransportListener(listener: FileSystemTransportListener)
    }
}