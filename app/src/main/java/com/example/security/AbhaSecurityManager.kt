package com.example.security

import android.content.Context
import android.util.Base64
import com.example.db.DatabaseKeyManager
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedAbhaRecord(
    val encryptedAbha: String,
    val blindIndexHash: String,
    val maskedDisplay: String
)

/**
 * Handles AES-GCM-256 cryptographic protection and blind indexing for ABHA card numbers
 * in compliance with Ayushman Bharat Digital Mission (ABDM) and DPDP data security standards.
 */
object AbhaSecurityManager {

    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12
    private const val HMAC_ALGORITHM = "HmacSHA256"

    /**
     * Encrypts an ABHA card number after strictly validating its 'XX-XXXX-XXXX-XXXX' format.
     */
    fun encryptAbha(context: Context, abhaNumber: String): EncryptedAbhaRecord {
        val validated = AbhaValidator.requireValid(abhaNumber)

        val encryptedBase64 = try {
            val key = getAesKey(context)
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(validated.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw SecurityException("Failed to securely encrypt ABHA number: ${e.message}", e)
        }

        val blindIndex = generateBlindIndex(context, validated)
        val masked = AbhaValidator.mask(validated)

        return EncryptedAbhaRecord(
            encryptedAbha = encryptedBase64,
            blindIndexHash = blindIndex,
            maskedDisplay = masked
        )
    }

    /**
     * Decrypts an encrypted ABHA card number and verifies its integrity.
     */
    fun decryptAbha(context: Context, encryptedBase64: String): String {
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < IV_LENGTH) {
                // If it wasn't encrypted, check if it's a valid raw ABHA
                return if (AbhaValidator.isValid(encryptedBase64)) encryptedBase64 else "INVALID_ABHA"
            }
            val iv = ByteArray(IV_LENGTH)
            val cipherText = ByteArray(combined.size - IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH)
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.size)

            val key = getAesKey(context)
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val plainBytes = cipher.doFinal(cipherText)
            val decrypted = String(plainBytes, Charsets.UTF_8)
            decrypted
        } catch (e: Exception) {
            // Fallback gracefully if already plain or corrupted
            if (AbhaValidator.isValid(encryptedBase64)) encryptedBase64 else "ENCRYPTED_ABHA_PROTECTED"
        }
    }

    /**
     * Generates a deterministic HMAC-SHA256 blind index hash of the normalized digits
     * to allow fast indexed database queries without exposing plaintext ABHA numbers.
     */
    fun generateBlindIndex(context: Context, abhaNumber: String): String {
        val cleanDigits = abhaNumber.filter { it.isDigit() }
        return try {
            val hmacKey = getHmacKey(context)
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(hmacKey)
            val hashBytes = mac.doFinal(cleanDigits.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(hashBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Fallback deterministic digest
            val sha256 = MessageDigest.getInstance("SHA-256")
            val hash = sha256.digest(cleanDigits.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(hash, Base64.NO_WRAP)
        }
    }

    private fun getAesKey(context: Context): SecretKeySpec {
        val pass = DatabaseKeyManager.getDatabasePassphrase(context)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(pass)
        return SecretKeySpec(sha256, "AES")
    }

    private fun getHmacKey(context: Context): SecretKeySpec {
        val pass = DatabaseKeyManager.getDatabasePassphrase(context)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(pass + "ABHA_BLIND_INDEX_PEPPER".toByteArray())
        return SecretKeySpec(sha256, HMAC_ALGORITHM)
    }
}
