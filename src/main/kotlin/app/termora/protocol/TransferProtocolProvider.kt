package app.termora.protocol

import app.termora.plugin.internal.local.LocalProtocolProvider
import app.termora.plugin.internal.sftppty.SFTPPtyProtocolProvider
import app.termora.plugin.internal.ssh.SSHProtocolProvider
import app.termora.protocol.ProtocolProvider.Companion.providers
import app.termora.sftp.internal.local.LocalTransferProtocolProvider
import app.termora.sftp.internal.sftp.SFTPTransferProtocolProvider
import org.apache.commons.lang3.StringUtils
import org.apache.commons.vfs2.provider.FileProvider

interface TransferProtocolProvider : ProtocolProvider {

    companion object {
        fun valueOf(protocol: String): TransferProtocolProvider? {
            var p = protocol

            if (StringUtils.equalsIgnoreCase(protocol, SSHProtocolProvider.PROTOCOL) ||
                StringUtils.equalsIgnoreCase(protocol, SFTPPtyProtocolProvider.PROTOCOL) ||
                StringUtils.equalsIgnoreCase(protocol, SFTPTransferProtocolProvider.PROTOCOL)
            ) {
                p = "sftp"
            } else if (StringUtils.equalsIgnoreCase(protocol, LocalProtocolProvider.PROTOCOL) ||
                StringUtils.equalsIgnoreCase(protocol, LocalTransferProtocolProvider.PROTOCOL)
            ) {
                p = "file"
            }

            return providers.filterIsInstance<TransferProtocolProvider>()
                .firstOrNull { StringUtils.equalsIgnoreCase(it.getProtocol(), p) }
        }
    }

    /**
     * 获取文件提供者
     */
    fun getFileProvider(): FileProvider

    /**
     * 获取根文件
     */
    fun getRootFileObject(requester: FileObjectRequest): FileObjectHandler

    override fun isTransfer(): Boolean {
        return true
    }
}