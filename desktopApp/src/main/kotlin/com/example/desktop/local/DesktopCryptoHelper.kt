package com.example.desktop.local

import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption helper for protecting offline SQLite session tokens and auth cache
 * in the user's Windows/Linux application data directory.
 */
object DesktopCryptoHelper {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_BIT_SIZE = 128

    fun resolveAppDataDir(): File {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val base = when {
            os.contains("win") -> {
                System.getenv("LOCALAPPDATA")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { File(it) }
                    ?: File(System.getProperty("user.home"), "AppData/Local")
            }
            os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support")
            else -> System.getenv("XDG_DATA_HOME")
                ?.takeIf { it.isNotBlank() }
                ?.let { File(it) }
                ?: File(System.getProperty("user.home"), ".local/share")
        }
        return File(base, "HisabPro").apply { mkdirs() }
    }

    private fun getOrCreateSecretKey(baseDir: File): SecretKey {
        val keyFile = File(baseDir, ".hisabpro_keystore.dat")
        if (keyFile.exists()) {
            return try {
                val raw = Base64.getDecoder().decode(keyFile.readText().trim())
                if (raw.size == 32) {
                    SecretKeySpec(raw, "AES")
                } else {
                    generateAndStoreKey(keyFile)
                }
            } catch (_: Exception) {
                generateAndStoreKey(keyFile)
            }
        }
        return generateAndStoreKey(keyFile)
    }

    private fun generateAndStoreKey(keyFile: File): SecretKey {
        keyFile.parentFile?.mkdirs()
        val generator = KeyGenerator.getInstance("AES").apply { init(256, SecureRandom()) }
        val key = generator.generateKey()
        keyFile.writeText(Base64.getEncoder().encodeToString(key.encoded))
        keyFile.setReadable(true, true)
        keyFile.setWritable(true, true)
        return key
    }

    fun encrypt(plainText: String, baseDir: File = resolveAppDataDir()): String {
        if (plainText.isEmpty()) return ""
        val key = getOrCreateSecretKey(baseDir)
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BIT_SIZE, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(cipherBase64: String, baseDir: File = resolveAppDataDir()): String {
        if (cipherBase64.isBlank()) return ""
        return try {
            val combined = Base64.getDecoder().decode(cipherBase64)
            if (combined.size <= IV_SIZE) return ""
            val iv = combined.copyOfRange(0, IV_SIZE)
            val payload = combined.copyOfRange(IV_SIZE, combined.size)
            val key = getOrCreateSecretKey(baseDir)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BIT_SIZE, iv))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}
