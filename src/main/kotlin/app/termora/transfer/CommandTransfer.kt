package app.termora.transfer

import org.apache.sshd.sftp.client.fs.SftpFileSystem
import org.apache.sshd.sftp.client.fs.SftpPath
import kotlin.math.max


class CommandTransfer(
    parentId: String,
    path: SftpPath,
    isDirectory: Boolean,
    private val size: Long,
    val command: String,
) : AbstractTransfer(parentId, path, path, isDirectory), TransferIndeterminate {

    private var executed = false

    override suspend fun transfer(bufferSize: Int): Long {
        if (executed) return 0
        val fs = source().fileSystem as SftpFileSystem
        fs.clientSession.executeRemoteCommand(command)
        executed = true
        return this.size()
    }

    override fun scanning(): Boolean {
        return false
    }

    override fun size(): Long {
        return max(size, 1)
    }

}