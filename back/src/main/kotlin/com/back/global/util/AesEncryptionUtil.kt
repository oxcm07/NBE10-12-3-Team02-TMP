package com.back.global.util

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class AesEncryptionUtil(
    @Value("\${custom.oauth.token.encryption-key}")
    encryptionKey: String,
) {
    private val secretKeySpec = SecretKeySpec(Base64.getDecoder().decode(encryptionKey), AES)

    fun encrypt(plainText: String): String {
        try {
            val iv = ByteArray(IV_LENGTH).also(secureRandom::nextBytes)
            val encrypted = Cipher.getInstance(ALGORITHM)
                .apply { init(Cipher.ENCRYPT_MODE, secretKeySpec, IvParameterSpec(iv)) }
                .doFinal(plainText.toByteArray(Charsets.UTF_8))

            return Base64.getEncoder().encodeToString(iv + encrypted)
        } catch (e: Exception) {
            throw IllegalStateException("암호화 실패", e)
        }
    }

    fun decrypt(encryptedText: String): String {
        try {
            val combined = Base64.getDecoder().decode(encryptedText)
            val iv = combined.copyOfRange(0, IV_LENGTH)
            val encrypted = combined.copyOfRange(IV_LENGTH, combined.size)

            return Cipher.getInstance(ALGORITHM)
                .apply { init(Cipher.DECRYPT_MODE, secretKeySpec, IvParameterSpec(iv)) }
                .doFinal(encrypted)
                .toString(Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalStateException("복호화 실패", e)
        }
    }

    companion object {
        private const val AES = "AES"
        private const val ALGORITHM = "AES/CBC/PKCS5Padding"
        private const val IV_LENGTH = 16
        private val secureRandom = SecureRandom()
    }
}
