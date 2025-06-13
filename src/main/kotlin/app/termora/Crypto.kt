package app.termora

import com.fasterxml.uuid.Generators
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.RandomUtils
import org.apache.commons.lang3.StringUtils
import java.io.InputStream
import java.security.*
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.*

private val jug = Generators.timeBasedEpochRandomGenerator(SecureRandom.getInstanceStrong())


fun randomUUID(): String {
    return jug.generate().toString().replace("-", StringUtils.EMPTY)
}

object AES {
    private const val ALGORITHM = "AES"


    object GCM {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128

        fun encrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key, ALGORITHM),
                GCMParameterSpec(GCM_TAG_LENGTH, iv)
            )
            return cipher.doFinal(data)
        }

        fun decrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, ALGORITHM),
                GCMParameterSpec(GCM_TAG_LENGTH, iv)
            )
            return cipher.doFinal(data)
        }
    }

    /**
     * ECB 没有 IV
     */
    object ECB {
        private const val TRANSFORMATION = "AES/ECB/PKCS5Padding"

        fun encrypt(key: ByteArray, data: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, ALGORITHM))
            return cipher.doFinal(data)
        }

        fun decrypt(key: ByteArray, data: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, ALGORITHM))
            return cipher.doFinal(data)
        }

    }

    /**
     * 携带 IV
     */
    object CBC {
        private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"

        fun encrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, ALGORITHM), IvParameterSpec(iv))
            return cipher.doFinal(data)
        }

        fun decrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, ALGORITHM), IvParameterSpec(iv))
            return cipher.doFinal(data)
        }


        fun String.aesCBCEncrypt(key: ByteArray, iv: ByteArray): ByteArray {
            return encrypt(key, iv, toByteArray())
        }

        fun ByteArray.aesCBCEncrypt(key: ByteArray, iv: ByteArray): ByteArray {
            return encrypt(key, iv, this)
        }

        fun ByteArray.aesCBCDecrypt(key: ByteArray, iv: ByteArray): ByteArray {
            return decrypt(key, iv, this)
        }

    }

    fun randomBytes(size: Int = 32): ByteArray {
        return RandomUtils.secureStrong().randomBytes(size)
    }

    fun ByteArray.encodeBase64String(): String {
        return Base64.encodeBase64String(this)
    }

    fun String.decodeBase64(): ByteArray {
        return Base64.decodeBase64(this)
    }
}


object PBKDF2 {

    private const val ALGORITHM = "PBKDF2WithHmacSHA512"

    fun hash(slat: ByteArray, password: CharArray, iterationCount: Int, keyLength: Int): ByteArray {
        val spec = PBEKeySpec(password, slat, iterationCount, keyLength)
        val secretKeyFactory = SecretKeyFactory.getInstance(ALGORITHM)
        return secretKeyFactory.generateSecret(spec).encoded
    }

}


object RSA {


    private const val TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

    fun encrypt(publicKey: PublicKey, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, getOAEPParameterSpec())
        return cipher.doFinal(data)
    }

    fun decrypt(privateKey: PrivateKey, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, privateKey, getOAEPParameterSpec())
        return cipher.doFinal(data)
    }

    fun encrypt(privateKey: PrivateKey, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, privateKey, getOAEPParameterSpec())
        return cipher.doFinal(data)
    }

    fun decrypt(publicKey: PublicKey, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, publicKey, getOAEPParameterSpec())
        return cipher.doFinal(data)
    }

    private fun getOAEPParameterSpec(): OAEPParameterSpec {
        return OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
        )

    }

    fun generatePublic(publicKey: ByteArray): PublicKey {
        return KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(publicKey))
    }

    fun generatePrivate(privateKey: ByteArray): PrivateKey {
        return KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(privateKey))
    }

    fun generateKeyPair(keySize: Int): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(keySize)
        return generator.generateKeyPair()
    }

    fun sign(privateKey: PrivateKey, data: ByteArray): ByteArray {
        val rsa = Signature.getInstance("SHA256withRSA")
        rsa.initSign(privateKey)
        rsa.update(data)
        return rsa.sign()
    }

    fun verify(publicKey: PublicKey, data: ByteArray, signature: ByteArray): Boolean {
        val rsa = Signature.getInstance("SHA256withRSA")
        rsa.initVerify(publicKey)
        rsa.update(data)
        return rsa.verify(signature)
    }
}


object Ed25519 {
    fun sign(privateKey: PrivateKey, data: ByteArray): ByteArray {
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(privateKey)
        signer.update(data)
        return signer.sign()
    }

    fun verify(publicKey: PublicKey, data: ByteArray, signature: ByteArray): Boolean {
        return verify(publicKey, data.inputStream(), signature)
    }

    fun verify(publicKey: PublicKey, input: InputStream, signature: ByteArray): Boolean {
        return runCatching {
            val verifier = Signature.getInstance("Ed25519")
            verifier.initVerify(publicKey)
            val buffer = ByteArray(1024)
            var len = 0
            while ((input.read(buffer).also { len = it }) != -1) {
                verifier.update(buffer, 0, len)
            }
            verifier.verify(signature)
        }.getOrNull() ?: false
    }


    fun generatePublic(publicKey: ByteArray): PublicKey {
        return KeyFactory.getInstance("Ed25519")
            .generatePublic(X509EncodedKeySpec(publicKey))
    }

    fun generatePrivate(privateKey: ByteArray): PrivateKey {
        return KeyFactory.getInstance("Ed25519")
            .generatePrivate(PKCS8EncodedKeySpec(privateKey))
    }

    fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("Ed25519")
        return generator.generateKeyPair()
    }
}