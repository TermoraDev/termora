package app.termora.protocol

import app.termora.plugin.internal.local.LocalProtocolProvider
import app.termora.plugin.internal.sftppty.SFTPPtyProtocolProvider
import app.termora.plugin.internal.ssh.SSHProtocolProvider
import app.termora.protocol.ProtocolProvider.Companion.providers
import app.termora.transfer.internal.local.LocalTransferProtocolProvider
import app.termora.transfer.internal.sftp.SFTPTransferProtocolProvider
import org.apache.commons.lang3.StringUtils

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
            } else if (StringUtils.equalsIgnoreCase(protocol, app.termora.plugin.internal.wsl.WSLProtocolProvider.PROTOCOL)) {
                p = "wsl-file"
            }

            return providers.filterIsInstance<TransferProtocolProvider>()
                .firstOrNull { StringUtils.equalsIgnoreCase(it.getProtocol(), p) }
        }
    }

    /**
     * 创建一个文件
     */
    fun createPathHandler(requester: PathHandlerRequest): PathHandler

    override fun isTransfer(): Boolean {
        return true
    }
}