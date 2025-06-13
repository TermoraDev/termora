package app.termora.plugins.oss

import org.apache.commons.vfs2.Capability
import org.apache.commons.vfs2.FileName
import org.apache.commons.vfs2.FileSystem
import org.apache.commons.vfs2.FileSystemOptions
import org.apache.commons.vfs2.provider.AbstractOriginatingFileProvider

class OSSFileProvider private constructor() : AbstractOriginatingFileProvider() {

    companion object {
        val instance by lazy { OSSFileProvider() }
        val capabilities = listOf(
            Capability.CREATE,
            Capability.DELETE,
            Capability.RENAME,
            Capability.GET_TYPE,
            Capability.LIST_CHILDREN,
            Capability.READ_CONTENT,
            Capability.URI,
            Capability.WRITE_CONTENT,
            Capability.GET_LAST_MODIFIED,
            Capability.SET_LAST_MODIFIED_FILE,
            Capability.RANDOM_ACCESS_READ,
            Capability.APPEND_CONTENT
        )
    }

    override fun getCapabilities(): Collection<Capability> {
        return OSSFileProvider.capabilities
    }

    override fun doCreateFileSystem(
        rootFileName: FileName,
        fileSystemOptions: FileSystemOptions
    ): FileSystem? {
        TODO("Not yet implemented")
    }


}