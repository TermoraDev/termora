package app.termora.vfs2.sftp

import org.apache.commons.vfs2.Capability
import org.apache.commons.vfs2.FileName
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.FileSystemOptions
import org.apache.commons.vfs2.provider.AbstractFileName
import org.apache.commons.vfs2.provider.AbstractFileSystem
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.sftp.client.fs.SftpFileSystem
import kotlin.io.path.absolutePathString

internal class MySftpFileSystem(
    private val sftpFileSystem: SftpFileSystem,
    rootName: FileName,
    fileSystemOptions: FileSystemOptions
) : AbstractFileSystem(rootName, null, fileSystemOptions) {

    override fun addCapabilities(caps: MutableCollection<Capability>) {
        caps.addAll(MySftpFileProvider.capabilities)
    }

    override fun createFile(name: AbstractFileName): FileObject {
        return MySftpFileObject(sftpFileSystem, name, this)
    }

    fun getDefaultDir(): String {
        return sftpFileSystem.defaultDir.absolutePathString()
    }

    fun getClientSession(): ClientSession {
        return sftpFileSystem.session
    }
}