package app.termora

import org.apache.sshd.sftp.client.impl.DefaultSftpClientFactory
import org.testcontainers.containers.GenericContainer
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SFTPTest {
    private val sftpContainer = GenericContainer("linuxserver/openssh-server")
        .withEnv("PUID", "1000")
        .withEnv("PGID", "1000")
        .withEnv("TZ", "Etc/UTC")
        .withEnv("SUDO_ACCESS", "true")
        .withEnv("PASSWORD_ACCESS", "true")
        .withEnv("USER_NAME", "foo")
        .withEnv("USER_PASSWORD", "pass")
        .withEnv("SUDO_ACCESS", "true")
        .withExposedPorts(2222)

    @BeforeTest
    fun setup() {
        sftpContainer.start()
    }

    @AfterTest
    fun teardown() {
        sftpContainer.stop()
    }

    @Test
    fun test() {
        val host = Host(
            name = sftpContainer.containerName,
            protocol = Protocol.SSH,
            host = "127.0.0.1",
            port = sftpContainer.getMappedPort(2222),
            username = "foo",
            authentication = Authentication.No.copy(type = AuthenticationType.Password, password = "pass"),
        )

        val client = SshClients.openClient(host)
        val session = SshClients.openSession(host, client)
        assertTrue(session.isOpen)


        val fileSystem = DefaultSftpClientFactory.INSTANCE.createSftpFileSystem(session)
        for (path in Files.list(fileSystem.rootDirectories.first())) {
            println(path)
        }
    }
}