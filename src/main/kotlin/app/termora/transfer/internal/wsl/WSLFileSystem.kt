package app.termora.transfer.internal.wsl

import java.nio.file.*
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.atomic.AtomicBoolean

class WSLFileSystem : FileSystem() {
    private val defaultFs = FileSystems.getDefault()

    private val isOpen = AtomicBoolean(true)


    override fun provider(): FileSystemProvider? = defaultFs.provider()
    override fun close() {
        isOpen.compareAndSet(true, false)

    }

    override fun isOpen(): Boolean {
        return isOpen.get()
    }

    override fun isReadOnly() = defaultFs.isReadOnly
    override fun getSeparator(): String? = defaultFs.separator
    override fun getRootDirectories(): Iterable<Path?>? = defaultFs.rootDirectories
    override fun getFileStores(): Iterable<FileStore?>? = defaultFs.fileStores
    override fun supportedFileAttributeViews(): Set<String?>? = defaultFs.supportedFileAttributeViews()
    override fun getPath(first: String, vararg more: String): Path =
        defaultFs.getPath(first, *more)

    override fun getPathMatcher(syntaxAndPattern: String): PathMatcher? =
        defaultFs.getPathMatcher(syntaxAndPattern)

    override fun getUserPrincipalLookupService(): UserPrincipalLookupService =
        defaultFs.userPrincipalLookupService

    override fun newWatchService(): WatchService =
        defaultFs.newWatchService()

//    override fun equals(other: Any?) = other == defaultFs
//    override fun hashCode() = defaultFs.hashCode()
}