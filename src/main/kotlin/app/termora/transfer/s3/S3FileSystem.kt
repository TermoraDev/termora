package app.termora.transfer.s3

import org.apache.sshd.common.file.util.BaseFileSystem
import java.nio.file.Path
import java.nio.file.attribute.UserPrincipalLookupService
import java.util.concurrent.atomic.AtomicBoolean

open class S3FileSystem(provider: S3FileSystemProvider) : BaseFileSystem<S3Path>(provider) {

    private val isOpen = AtomicBoolean(true)

    override fun create(root: String?, names: List<String>): S3Path {
        val path = S3Path(this, root, names)
        if (names.isEmpty()) {
            path.attributes = path.attributes.copy(directory = true)
        }
        return path
    }

    override fun isOpen(): Boolean {
        return isOpen.get()
    }

    override fun close() {
        isOpen.compareAndSet(true, false)
    }

    override fun getRootDirectories(): Iterable<Path> {
        return mutableSetOf<Path>(create(separator))
    }

    override fun supportedFileAttributeViews(): Set<String> {
        TODO("Not yet implemented")
    }

    override fun getUserPrincipalLookupService(): UserPrincipalLookupService {
        throw UnsupportedOperationException()
    }
}
