package app.termora.transfer.s3

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission

data class S3FileAttributes(
    private val lastModifiedTime: Long = 0,
    private val lastAccessTime: Long = 0,
    private val creationTime: Long = 0,

    private val regularFile: Boolean = false,
    private val directory: Boolean = false,
    private val symbolicLink: Boolean = false,
    private val other: Boolean = false,
    private val size: Long = 0,

    ) : BasicFileAttributes {

    var permissions: Set<PosixFilePermission> = emptySet()


    override fun lastModifiedTime(): FileTime {
        return FileTime.fromMillis(lastModifiedTime)
    }

    override fun lastAccessTime(): FileTime {
        return FileTime.fromMillis(lastAccessTime)
    }

    override fun creationTime(): FileTime {
        return FileTime.fromMillis(creationTime)
    }

    override fun isRegularFile(): Boolean {
        return regularFile
    }

    override fun isDirectory(): Boolean {
        return directory
    }

    override fun isSymbolicLink(): Boolean {
        return symbolicLink
    }

    override fun isOther(): Boolean {
        return other
    }

    override fun size(): Long {
        return size
    }

    override fun fileKey(): Any? {
        return null
    }


}