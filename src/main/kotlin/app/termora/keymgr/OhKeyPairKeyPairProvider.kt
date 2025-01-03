package app.termora.keymgr

import app.termora.AES.decodeBase64
import app.termora.RSA
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import org.apache.sshd.common.keyprovider.AbstractResourceKeyPairProvider
import org.apache.sshd.common.session.SessionContext
import org.slf4j.LoggerFactory
import java.security.Key
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class OhKeyPairKeyPairProvider(private val id: String) : AbstractResourceKeyPairProvider<String>() {
    companion object {
        private val log = LoggerFactory.getLogger(OhKeyPairKeyPairProvider::class.java)
        private val cache = ConcurrentHashMap<String, Key>()

        fun generateKeyPair(ohKeyPair: OhKeyPair): KeyPair {
            val publicKey = cache.getOrPut(ohKeyPair.publicKey) {
                when (ohKeyPair.type) {
                    "RSA" -> RSA.generatePublic(ohKeyPair.publicKey.decodeBase64())
                    "ED25519" -> EdDSAPublicKey(X509EncodedKeySpec(ohKeyPair.publicKey.decodeBase64()))
                    else -> throw UnsupportedOperationException("${ohKeyPair.type} is not supported")
                }
            } as PublicKey

            val privateKey = cache.getOrPut(ohKeyPair.privateKey) {
                when (ohKeyPair.type) {
                    "RSA" -> RSA.generatePrivate(ohKeyPair.privateKey.decodeBase64())
                    "ED25519" -> EdDSAPrivateKey(PKCS8EncodedKeySpec(ohKeyPair.privateKey.decodeBase64()))
                    else -> throw UnsupportedOperationException("${ohKeyPair.type} is not supported")
                }
            } as PrivateKey

            return KeyPair(publicKey, privateKey)

        }
    }


    override fun loadKeys(session: SessionContext?): Iterable<KeyPair> {
        val log = OhKeyPairKeyPairProvider.log
        val ohKeyPair = KeyManager.instance.getOhKeyPair(id)
        if (ohKeyPair == null) {
            if (log.isErrorEnabled) {
                log.error("Oh KeyPair [$id] could not be loaded")
            }
            return emptyList()
        }

        return object : Iterable<KeyPair> {
            override fun iterator(): Iterator<KeyPair> {
                val result = kotlin.runCatching { generateKeyPair(ohKeyPair) }
                if (result.isSuccess) {
                    return listOf(result.getOrThrow()).iterator()
                } else if (log.isErrorEnabled) {
                    log.error("Oh KeyPair [$id] could not be loaded.", result.exceptionOrNull())
                }
                return Collections.emptyIterator()
            }
        }
    }
}