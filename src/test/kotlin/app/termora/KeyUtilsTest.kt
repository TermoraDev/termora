package app.termora

import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter
import org.apache.sshd.common.keyprovider.KeyPairProvider
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyUtilsTest {
    @Test
    fun test() {
        assertEquals(KeyUtils.getKeySize(KeyUtils.generateKeyPair("ssh-rsa", 1024).private), 1024)
        assertEquals(KeyUtils.getKeySize(KeyUtils.generateKeyPair("ssh-rsa", 1024).public), 1024)
    }

    @Test
    fun test_ed25519() {
        val keyPair = KeyUtils.generateKeyPair(KeyPairProvider.SSH_ED25519, 256)
        assertEquals(KeyUtils.getKeyType(keyPair), KeyPairProvider.SSH_ED25519)
        assertEquals(KeyUtils.getKeySize(keyPair.private), 256)
        assertEquals(KeyUtils.getKeySize(keyPair.public), 256)

        val baos = ByteArrayOutputStream()

        OpenSSHKeyPairResourceWriter.INSTANCE
            .writePublicKey(keyPair.public, null, baos)

        assertTrue(baos.toString().startsWith(KeyPairProvider.SSH_ED25519))

        baos.reset()

        OpenSSHKeyPairResourceWriter.INSTANCE
            .writePrivateKey(keyPair, null, null, baos)

        println(baos.toString())


    }
}