package app.termora.transfer.internal.wsl

import app.termora.database.DatabaseManager
import app.termora.protocol.PathHandler
import app.termora.protocol.PathHandlerRequest
import app.termora.protocol.TransferProtocolProvider
import org.apache.commons.lang3.StringUtils
import java.nio.file.FileSystems

internal class WSLTransferProtocolProvider : TransferProtocolProvider {
    companion object {
        val instance by lazy { WSLTransferProtocolProvider() }
        const val PROTOCOL = "wsl"
    }

    override fun isTransient(): Boolean {
        return true
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun createPathHandler(requester: PathHandlerRequest): PathHandler {
        val wslPath = requester.host.options.extras["wslPath"] ?: StringUtils.EMPTY
//        val fileSystem = FileSystems.getDefault()
        val fileSystem = WSLFileSystemFactory.instance.createWSLFileSystem()
        return PathHandler(fileSystem, fileSystem.getPath(wslPath))
    }
}