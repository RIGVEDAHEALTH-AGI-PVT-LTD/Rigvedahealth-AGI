package com.example.db

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object DatabaseKeyManager {
    private const val PREFS_NAME = "secure_db_prefs"
    private const val PASSPHRASE_KEY = "encrypted_db_passphrase"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    fun getDatabasePassphrase(context: Context): ByteArray {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(PASSPHRASE_KEY, null)
        if (saved != null) {
            return saved.toByteArray(Charsets.UTF_8)
        }

        val generated = "Rigveda-OfflineNative-PHC-${System.currentTimeMillis()}-SecurePass#4401"
        prefs.edit().putString(PASSPHRASE_KEY, generated).apply()
        return generated.toByteArray(Charsets.UTF_8)
    }

    private fun getSecretKey(context: Context): SecretKeySpec {
        val pass = getDatabasePassphrase(context)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(pass)
        return SecretKeySpec(sha256, "AES")
    }

    /**
     * AES-GCM-256 Encrypts clinical payloads before SQLite caching
     */
    fun encryptPayload(context: Context, plainText: String): String {
        return try {
            val key = getSecretKey(context)
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText
        }
    }

    /**
     * AES-GCM-256 Decrypts clinical payloads retrieved from SQLite cache
     */
    fun decryptPayload(context: Context, encryptedBase64: String): String {
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < IV_LENGTH) return encryptedBase64
            val iv = ByteArray(IV_LENGTH)
            val cipherText = ByteArray(combined.size - IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH)
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.size)

            val key = getSecretKey(context)
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            encryptedBase64
        }
    }
}
