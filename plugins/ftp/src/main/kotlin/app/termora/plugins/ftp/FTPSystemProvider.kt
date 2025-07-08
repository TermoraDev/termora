package app.termora.plugins.ftp

import app.termora.transfer.s3.S3FileSystemProvider
import app.termora.transfer.s3.S3Path
import org.apache.commons.io.IOUtils
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.pool2.impl.GenericObjectPool
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

class FTPSystemProvider(private val pool: GenericObjectPool<FTPClient>) : S3FileSystemProvider() {


    override fun getScheme(): String? {
        return "ftp"
    }

    override fun getOutputStream(path: S3Path): OutputStream {
        return createStreamer(path)
    }

    override fun getInputStream(path: S3Path): InputStream {
        val ftp = pool.borrowObject()
        val fs = ftp.retrieveFileStream(path.absolutePathString())
        return object : InputStream() {
            override fun read(): Int {
                return fs.read()
            }

            override fun close() {
                IOUtils.closeQuietly(fs)
                ftp.completePendingCommand()
                pool.returnObject(ftp)
            }
        }
    }

    private fun createStreamer(path: S3Path): OutputStream {
        val ftp = pool.borrowObject()
        val os = ftp.storeFileStream(path.absolutePathString())
        return object : OutputStream() {
            override fun write(b: Int) {
                os.write(b)
            }

            override fun close() {
                IOUtils.closeQuietly(os)
                ftp.completePendingCommand()
                pool.returnObject(ftp)
            }
        }
    }

    override fun fetchChildren(path: S3Path): MutableList<S3Path> {
        val paths = mutableListOf<S3Path>()
        if (path.exists().not()) {
            throw NoSuchFileException(path.absolutePathString())
        }

        withFtpClient {
            val files = it.listFiles(path.absolutePathString())
            for (file in files) {
                val p = path.resolve(file.name)
                p.attributes = p.attributes.copy(
                    directory = file.isDirectory,
                    regularFile = file.isFile,
                    size = file.size,
                    lastModifiedTime = file.timestamp.timeInMillis,
                )
                p.attributes.permissions = ftpPermissionsToPosix(file)
                paths.add(p)
            }
        }

        return paths

    }


    private fun ftpPermissionsToPosix(file: FTPFile): Set<PosixFilePermission> {
        val perms = mutableSetOf<PosixFilePermission>()

        if (file.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION))
            perms.add(PosixFilePermission.OWNER_READ)
        if (file.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION))
            perms.add(PosixFilePermission.OWNER_WRITE)
        if (file.hasPermission(FTPFile.USER_ACCESS, FTPFile.EXECUTE_PERMISSION))
            perms.add(PosixFilePermission.OWNER_EXECUTE)

        if (file.hasPermission(FTPFile.GROUP_ACCESS, FTPFile.READ_PERMISSION))
            perms.add(PosixFilePermission.GROUP_READ)
        if (file.hasPermission(FTPFile.GROUP_ACCESS, FTPFile.WRITE_PERMISSION))
            perms.add(PosixFilePermission.GROUP_WRITE)
        if (file.hasPermission(FTPFile.GROUP_ACCESS, FTPFile.EXECUTE_PERMISSION))
            perms.add(PosixFilePermission.GROUP_EXECUTE)

        if (file.hasPermission(FTPFile.WORLD_ACCESS, FTPFile.READ_PERMISSION))
            perms.add(PosixFilePermission.OTHERS_READ)
        if (file.hasPermission(FTPFile.WORLD_ACCESS, FTPFile.WRITE_PERMISSION))
            perms.add(PosixFilePermission.OTHERS_WRITE)
        if (file.hasPermission(FTPFile.WORLD_ACCESS, FTPFile.EXECUTE_PERMISSION))
            perms.add(PosixFilePermission.OTHERS_EXECUTE)

        return perms
    }

    override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>) {
        withFtpClient { it.mkd(dir.absolutePathString()) }
    }

    override fun move(source: Path?, target: Path?, vararg options: CopyOption?) {
        if (source != null && target != null) {
            withFtpClient {
                it.rename(source.absolutePathString(), target.absolutePathString())
            }
        }
    }

    override fun delete(path: S3Path, isDirectory: Boolean) {
        withFtpClient {
            if (isDirectory) {
                it.rmd(path.absolutePathString())
            } else {
                it.deleteFile(path.absolutePathString())
            }
        }
    }

    override fun checkAccess(path: S3Path, vararg modes: AccessMode) {
        withFtpClient {
            if (it.cwd(path.absolutePathString()) == 250) {
                return
            }
            if (it.listFiles(path.absolutePathString()).isNotEmpty()) {
                return
            }
        }
        throw NoSuchFileException(path.absolutePathString())
    }

    private inline fun <T> withFtpClient(block: (FTPClient) -> T): T {
        val client = pool.borrowObject()
        return try {
            block(client)
        } finally {
            pool.returnObject(client)
        }
    }
}