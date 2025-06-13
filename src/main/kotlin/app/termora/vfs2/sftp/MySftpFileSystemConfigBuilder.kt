package app.termora.vfs2.sftp

import org.apache.commons.vfs2.FileSystem
import org.apache.commons.vfs2.FileSystemConfigBuilder
import org.apache.commons.vfs2.FileSystemOptions
import org.apache.sshd.sftp.client.fs.SftpFileSystem

internal class MySftpFileSystemConfigBuilder : FileSystemConfigBuilder() {

    companion object {
        private val INSTANCE by lazy { MySftpFileSystemConfigBuilder() }
        fun getInstance(): MySftpFileSystemConfigBuilder {
            return INSTANCE
        }
    }

    override fun getConfigClass(): Class<out FileSystem> {
        return MySftpFileSystem::class.java
    }



    fun setSftpFileSystem(options: FileSystemOptions, sftpFileSystem: SftpFileSystem) {
        setParam(options, "sftpFileSystem", sftpFileSystem)
    }

    fun getSftpFileSystem(options: FileSystemOptions): SftpFileSystem? {
        return getParam(options, "sftpFileSystem")
    }
}