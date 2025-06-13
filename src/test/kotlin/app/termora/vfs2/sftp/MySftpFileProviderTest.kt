package app.termora.vfs2.sftp

import app.termora.SSHDTest
import app.termora.randomUUID
import org.apache.commons.vfs2.*
import org.apache.commons.vfs2.impl.DefaultFileSystemManager
import org.apache.commons.vfs2.provider.local.DefaultLocalFileProvider
import org.apache.sshd.sftp.client.SftpClientFactory
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MySftpFileProviderTest : SSHDTest() {

    companion object {
        init {
            val fileSystemManager = DefaultFileSystemManager()
            fileSystemManager.addProvider("sftp", MySftpFileProvider.instance)
            fileSystemManager.addProvider("file", DefaultLocalFileProvider())
            fileSystemManager.init()
            VFS.setManager(fileSystemManager)
        }
    }

    @Test
    fun testSetExecutable() {
        val file = newFileObject("/config/test.txt")
        file.createFile()
        file.refresh()
        assertFalse(file.isExecutable)
        file.setExecutable(true, false)
        file.refresh()
        assertTrue(file.isExecutable)
    }

    @Test
    fun testCreateFile() {
        val file = newFileObject("/config/test.txt")
        assertFalse(file.exists())
        file.createFile()
        assertTrue(file.exists())
    }

    @Test
    fun testWriteAndReadFile() {
        val file = newFileObject("/config/test.txt")
        file.createFile()
        assertFalse(file.content.isOpen)

        val os = file.content.outputStream
        os.write("test".toByteArray())
        os.flush()
        assertTrue(file.content.isOpen)

        os.close()
        assertFalse(file.content.isOpen)

        val input = file.content.inputStream
        assertEquals("test", String(input.readAllBytes()))
        assertTrue(file.content.isOpen)
        input.close()
        assertFalse(file.content.isOpen)

    }

    @Test
    fun testCreateFolder() {
        val file = newFileObject("/config/test")
        assertFalse(file.exists())
        file.createFolder()
        assertTrue(file.exists())
    }


    @Test
    fun testSftpClient() {
        val session = newClientSession()
        val client = SftpClientFactory.instance().createSftpClient(session)
        assertTrue(client.isOpen)
        session.close()
        assertFalse(client.isOpen)
    }

    @Test
    fun testCopy() {
        val file = newFileObject("/config/sshd.pid")
        val filepath = File("build", randomUUID())
        val localFile = getVFS().resolveFile("file://${filepath.absolutePath}")

        localFile.copyFrom(file, Selectors.SELECT_ALL)
        assertEquals(
            file.content.getString(Charsets.UTF_8),
            localFile.content.getString(Charsets.UTF_8)
        )

        localFile.delete()
    }

    private fun getVFS(): FileSystemManager {
        return VFS.getManager()
    }

    private fun newFileObject(path: String): FileObject {
        val vfs = getVFS()
        val fileSystemOptions = FileSystemOptions()
        MySftpFileSystemConfigBuilder.getInstance()
            .setSftpFileSystem(fileSystemOptions, SftpClientFactory.instance().createSftpFileSystem(newClientSession()))
        return vfs.resolveFile("sftp://${path}", fileSystemOptions)
    }


}