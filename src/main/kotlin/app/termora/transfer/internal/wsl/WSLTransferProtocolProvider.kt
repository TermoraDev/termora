package app.termora.transfer.internal.wsl

import app.termora.protocol.PathHandler
import app.termora.protocol.PathHandlerRequest
import app.termora.protocol.TransferProtocolProvider
import java.nio.file.FileSystems

internal class WSLTransferProtocolProvider : TransferProtocolProvider {
    companion object {
        val instance by lazy { WSLTransferProtocolProvider() }
        const val PROTOCOL = "wsl-file"
    }

    override fun isTransient(): Boolean {
        return true
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun createPathHandler(requester: PathHandlerRequest): PathHandler {
        val distributionName = requester.host.host
        val username = requester.host.username
        val wslDistroRoot = FileSystems.getDefault().getPath("\\\\wsl.localhost\\$distributionName\\")
        val wslBasePath = if (username.isNotBlank()) {
            if (username == "root") {
                wslDistroRoot.resolve("root")
            } else {
                wslDistroRoot.resolve("home").resolve(username)
            }
        } else {
            wslDistroRoot
        }
        return PathHandler(FileSystems.getDefault(), wslBasePath)
    }
}
