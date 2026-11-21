package com.example.shared.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object SharedPasswordHasher {
    private const val ITERATIONS = 12000
    private const val KEY_LENGTH_BITS = 256
    private val random = SecureRandom()

    data class HashedPassword(
        val saltBase64: String,
        val hashBase64: String
    )

    fun hashPassword(password: String): HashedPassword {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        val saltB64 = Base64.getEncoder().encodeToString(salt)
        val hashB64 = computeHash(password, salt)
        return HashedPassword(saltB64, hashB64)
    }

    fun verifyPassword(password: String, saltBase64: String, expectedHashBase64: String): Boolean {
        return try {
            val salt = Base64.getDecoder().decode(saltBase64)
            val computed = computeHash(password, salt)
            MessageDigest.isEqual(
                computed.toByteArray(Charsets.UTF_8),
                expectedHashBase64.toByteArray(Charsets.UTF_8)
            )
        } catch (_: Exception) {
            false
        }
    }

    private fun computeHash(password: String, salt: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun generateTemporaryPassword(length: Int = 8): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
        return (1..length)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }

    fun generateCode(prefix: String): String {
        val num = 1000 + random.nextInt(9000)
        val suffix = ('A'..'Z').let { letters ->
            "${letters.random()}${letters.random()}"
        }
        return "$prefix-$num$suffix"
    }

    fun generateBusinessCode(businessName: String): String {
        val clean = businessName.uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]"), "")
            .take(4)
            .ifEmpty { "HP" }
        val num = 100 + random.nextInt(900)
        return "$clean-$num"
    }

    fun buildTicketId(businessCode: String, username: String): String {
        val codeClean = businessCode.trim().lowercase(Locale.US).replace(Regex("[^a-z0-9_-]"), "-")
        val userClean = username.trim().lowercase(Locale.US).replace(Regex("[^a-z0-9_-]"), "_")
        return "${codeClean}__${userClean}"
    }

    fun validateStrongPassword(password: String): String? {
        if (password.length < 8) return "Password must be at least 8 characters long."
        if (!password.any { it.isUpperCase() }) return "Password must include at least one uppercase letter."
        if (!password.any { it.isLowerCase() }) return "Password must include at least one lowercase letter."
        if (!password.any { it.isDigit() }) return "Password must include at least one number."
        return null
    }
}
