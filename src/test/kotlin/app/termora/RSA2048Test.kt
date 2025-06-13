package app.termora

import org.apache.commons.codec.binary.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals


class RSA2048Test {
    @Test
    fun test() {
        val data = "hello world. 中国 😄".toByteArray()

        val pair = RSA.generateKeyPair(2048)

        println("publicKey: ${Base64.encodeBase64String(pair.public.encoded)}")
        println("privateKey: ${Base64.encodeBase64String(pair.private.encoded)}")

        assertContentEquals(RSA.decrypt(pair.private, RSA.encrypt(pair.public, data)), data)
        assertContentEquals(RSA.decrypt(pair.public, RSA.encrypt(pair.private, data)), data)
    }


}