package com.majiang.counter.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * 本地密码哈希（SHA-256 + 随机盐），仅用于设备端账号校验，非联网认证。
 */
object PasswordHasher {

    private const val SALT_BYTES = 16
    private const val ITERATIONS = 12_000

    fun generateSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun hash(password: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = (password + Base64.getEncoder().encodeToString(salt)).toByteArray(Charsets.UTF_8)
        repeat(ITERATIONS) {
            digest.reset()
            bytes = digest.digest(bytes)
        }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verify(password: String, saltBase64: String, expectedHash: String): Boolean {
        val salt = decodeSalt(saltBase64) ?: return false
        return hash(password, salt) == expectedHash
    }

    fun encodeSalt(salt: ByteArray): String = Base64.getEncoder().encodeToString(salt)

    fun decodeSalt(saltBase64: String): ByteArray? =
        runCatching { Base64.getDecoder().decode(saltBase64) }.getOrNull()
}
