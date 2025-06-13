package app.termora.sftp.internal.sftp

import app.termora.SshClients
import app.termora.protocol.FileObjectRequest
import app.termora.protocol.TransferProtocolProvider
import app.termora.vfs2.sftp.MySftpFileProvider
import app.termora.vfs2.sftp.MySftpFileSystemConfigBuilder
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.vfs2.FileSystemOptions
import org.apache.commons.vfs2.VFS
import org.apache.commons.vfs2.provider.FileProvider
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.sftp.client.SftpClientFactory
import kotlin.io.path.absolutePathString

internal class SFTPTransferProtocolProvider : TransferProtocolProvider {
    companion object {
        val instance by lazy { SFTPTransferProtocolProvider() }
        const val PROTOCOL = "sftp"

    }

    override fun getFileProvider(): FileProvider {
        return MySftpFileProvider.instance
    }

    override fun getRootFileObject(requester: FileObjectRequest): SFTPFileObjectHandler {
        var client: SshClient? = null
        var session: ClientSession? = null
        try {
            val owner = requester.owner
            client = if (owner == null) SshClients.openClient(requester.host)
            else SshClients.openClient(requester.host, owner)
            session = SshClients.openSession(requester.host, client)
            val fileSystem = SftpClientFactory.instance().createSftpFileSystem(session)

            val host = requester.host
            var defaultDirectory = host.options.sftpDefaultDirectory
            if (StringUtils.isBlank(defaultDirectory)) {
                defaultDirectory = fileSystem.defaultDir.absolutePathString()
            }

            val options = FileSystemOptions()
            MySftpFileSystemConfigBuilder.getInstance().setSftpFileSystem(options, fileSystem)
            val file = VFS.getManager().resolveFile("sftp://${defaultDirectory}", options)
            return SFTPFileObjectHandler(file, client, session, fileSystem)
        } catch (e: Exception) {
            IOUtils.closeQuietly(session)
            IOUtils.closeQuietly(client)
            throw e
        }
    }

    override fun getProtocol(): String {
        return "sftp"
    }

}