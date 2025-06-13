package app.termora.plugins.migration

import org.slf4j.LoggerFactory
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.time.measureTime

object PBKDF2 {

    private const val ALGORITHM = "PBKDF2WithHmacSHA512"
    private val log = LoggerFactory.getLogger(PBKDF2::class.java)

    fun generateSecret(
        password: CharArray,
        salt: ByteArray,
        iterationCount: Int = 150000,
        keyLength: Int = 256
    ): ByteArray {
        val bytes: ByteArray
        val time = measureTime {
            bytes = SecretKeyFactory.getInstance(ALGORITHM)
                .generateSecret(PBEKeySpec(password, salt, iterationCount, keyLength))
                .encoded
        }
        if (log.isDebugEnabled) {
            log.debug("Secret generated $time")
        }
        return bytes
    }

    fun hash(slat: ByteArray, password: CharArray, iterationCount: Int, keyLength: Int): ByteArray {
        val spec = PBEKeySpec(password, slat, iterationCount, keyLength)
        val secretKeyFactory = SecretKeyFactory.getInstance(ALGORITHM)
        return secretKeyFactory.generateSecret(spec).encoded
    }

}
