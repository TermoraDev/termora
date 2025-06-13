package app.termora.plugins.s3

import app.termora.Authentication
import app.termora.AuthenticationType
import app.termora.Host
import app.termora.protocol.FileObjectRequest
import app.termora.vfs2.VFSWalker
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.VFS
import org.apache.commons.vfs2.cache.WeakRefFilesCache
import org.apache.commons.vfs2.impl.DefaultFileSystemManager
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import kotlin.test.Test

@Testcontainers
class S3FileProviderTest {

    private val ak = UUID.randomUUID().toString()
    private val sk = UUID.randomUUID().toString()

    @Container
    private val monio: GenericContainer<*> = GenericContainer("minio/minio")
        .withEnv("MINIO_ACCESS_KEY", ak)
        .withEnv("MINIO_SECRET_KEY", sk)
        .withExposedPorts(9000, 9090)
        .withCommand("server", "/data", "--console-address", ":9090", "-address", ":9000")

    companion object {

    }

    @Test
    fun test() {
        val endpoint = "http://127.0.0.1:${monio.getMappedPort(9000)}"
        val minioClient = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(ak, sk)
            .build()

        val fileSystemManager = DefaultFileSystemManager()
        fileSystemManager.addProvider("s3", S3ProtocolProvider.instance.getFileProvider())
        fileSystemManager.filesCache = WeakRefFilesCache()
        fileSystemManager.init()
        VFS.setManager(fileSystemManager)

        for (i in 0 until 5) {
            val bucket = "bucket-$i"
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())

            minioClient.putObject(
                PutObjectArgs.builder().bucket(bucket)
                    .`object`("test-1/test-2/test-3/file-$i")
                    .stream(ByteArrayInputStream("hello".toByteArray()), -1, 5 * 1024 * 1024)
                    .build()
            )
        }

        val requester = FileObjectRequest(
            host = Host(
                name = "test",
                protocol = S3ProtocolProvider.PROTOCOL,
                host = endpoint,
                username = ak,
                authentication = Authentication.No.copy(type = AuthenticationType.Password, password = sk),
            ),
        )
        val file = S3ProtocolProvider.instance.getRootFileObject(requester).file
        VFSWalker.walk(file, object : FileVisitor<FileObject> {
            override fun preVisitDirectory(
                dir: FileObject,
                attrs: BasicFileAttributes
            ): FileVisitResult {
                println("preVisitDirectory: ${dir.name}")
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(
                file: FileObject,
                attrs: BasicFileAttributes
            ): FileVisitResult {
                println("visitFile: ${file.name}")
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(
                file: FileObject,
                exc: IOException
            ): FileVisitResult {
                return FileVisitResult.TERMINATE
            }

            override fun postVisitDirectory(
                dir: FileObject,
                exc: IOException?
            ): FileVisitResult {
                return FileVisitResult.CONTINUE
            }

        })
    }
}