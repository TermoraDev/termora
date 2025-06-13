package app.termora.plugins.migration

import app.termora.*
import app.termora.AES.decodeBase64
import app.termora.AES.encodeBase64String

class PasswordWrongException : RuntimeException()

class Doorman private constructor() : Disposable {
    private val properties get() = Database.getDatabase().properties
    private var key = byteArrayOf()

    companion object {
        fun getInstance(): Doorman {
            return ApplicationScope.forApplicationScope().getOrCreate(Doorman::class) { Doorman() }
        }
    }

    fun isWorking(): Boolean {
        return properties.getString("doorman", "false").toBoolean()
    }

    fun encrypt(text: String): String {
        checkIsWorking()
        return AES.ECB.encrypt(key, text.toByteArray()).encodeBase64String()
    }


    fun decrypt(text: String): String {
        checkIsWorking()
        return AES.ECB.decrypt(key, text.decodeBase64()).decodeToString()
    }

    /**
     * @return 返回钥匙
     */
    fun work(password: CharArray): ByteArray {
        if (key.isNotEmpty()) {
            throw IllegalStateException("Working")
        }
        return work(convertKey(password))
    }

    fun work(key: ByteArray): ByteArray {
        val verify = properties.getString("doorman-verify")
        if (verify == null) {
            properties.putString(
                "doorman-verify",
                AES.ECB.encrypt(key, factor()).encodeBase64String()
            )
        } else {
            try {
                if (!AES.ECB.decrypt(key, verify.decodeBase64()).contentEquals(factor())) {
                    throw PasswordWrongException()
                }
            } catch (e: Exception) {
                throw PasswordWrongException()
            }
        }

        this.key = key
        properties.putString("doorman", "true")

        return this.key
    }


    private fun convertKey(password: CharArray): ByteArray {
        return PBKDF2.generateSecret(password, factor())
    }


    private fun checkIsWorking() {
        if (key.isEmpty() || !isWorking()) {
            throw UnsupportedOperationException("Doorman is not working")
        }
    }

    private fun factor(): ByteArray {
        return Application.getName().toByteArray()
    }

    fun test(password: CharArray): Boolean {
        checkIsWorking()
        return key.contentEquals(convertKey(password))
    }

    override fun dispose() {
        key = byteArrayOf()
    }
}