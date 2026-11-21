package com.example.data.local

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object LocalCryptoHelper {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "hisabpro_offline_cache_aes_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val PREFS_NAME = "hisabpro_device_meta"
    private const val KEY_DEVICE_ID = "device_id"

    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = "${Build.MODEL.replace(" ", "_").take(12)}_${UUID.randomUUID().toString().take(8)}"
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return runCatching {
            val key = getOrCreateKeyStoreKey() ?: return@runCatching fallbackEncode(plainText)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteBuffer.allocate(1 + iv.size + encrypted.size)
                .put(iv.size.toByte())
                .put(iv)
                .put(encrypted)
                .array()
            "KS1:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        }.getOrElse {
            fallbackEncode(plainText)
        }
    }

    fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        return runCatching {
            if (cipherText.startsWith("KS1:")) {
                val raw = Base64.decode(cipherText.removePrefix("KS1:"), Base64.NO_WRAP)
                val buffer = ByteBuffer.wrap(raw)
                val ivLen = buffer.get().toInt()
                val iv = ByteArray(ivLen)
                buffer.get(iv)
                val encrypted = ByteArray(buffer.remaining())
                buffer.get(encrypted)
                val key = getOrCreateKeyStoreKey() ?: return@runCatching ""
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
                String(cipher.doFinal(encrypted), Charsets.UTF_8)
            } else if (cipherText.startsWith("JVM1:")) {
                String(Base64.decode(cipherText.removePrefix("JVM1:"), Base64.NO_WRAP), Charsets.UTF_8)
            } else {
                cipherText
            }
        }.getOrDefault("")
    }

    private fun fallbackEncode(plainText: String): String {
        return "JVM1:" + Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun getOrCreateKeyStoreKey(): SecretKey? {
        return runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return@runCatching it }
            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        }.getOrNull()
    }
}
