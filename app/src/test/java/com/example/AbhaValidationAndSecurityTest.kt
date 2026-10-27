package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.AbhaSecurityManager
import com.example.security.AbhaValidationResult
import com.example.security.AbhaValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AbhaValidationAndSecurityTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testValidAbhaFormats() {
        val validAbhaList = listOf(
            "91-4432-8810-1209",
            "14-1234-5678-9012",
            "32-9876-5432-1098"
        )

        for (abha in validAbhaList) {
            assertTrue("Expected $abha to be valid", AbhaValidator.isValid(abha))
            val result = AbhaValidator.validate(abha)
            assertTrue("Expected Valid result for $abha", result is AbhaValidationResult.Valid)
            val valid = result as AbhaValidationResult.Valid
            assertEquals(abha, valid.formattedNumber)
            assertEquals("${abha.substring(0, 3)}••••-••••-${abha.substring(13)}", valid.maskedNumber)
        }
    }

    @Test
    fun testRejectInvalidAbhaFormats() {
        val invalidInputs = listOf(
            null,
            "",
            "   ",
            "12345",
            "91-443-8810-1209",         // Block 2 has 3 digits instead of 4
            "91-4432-881-1209",          // Block 3 has 3 digits instead of 4
            "91-4432-8810-120",          // Block 4 has 3 digits instead of 4
            "9-4432-8810-1209",          // Block 1 has 1 digit instead of 2
            "91-4432-8810-12099",        // Extra digit
            "91-4432-8810-120A",         // Alpha character
            "91443288101209",            // Unformatted (isValid requires exact XX-XXXX-XXXX-XXXX)
            "00-0000-0000-0000",         // Repetitive dummy digits
            "11-1111-1111-1111",         // Repetitive dummy digits
            "99-9999-9999-9999"          // Repetitive dummy digits
        )

        for (input in invalidInputs) {
            assertFalse("Expected '$input' to be invalid", AbhaValidator.isValid(input))
        }
    }

    @Test
    fun testAutoFormatOnInput() {
        // Typing pure 14 digits should format automatically
        val typed14 = "91443288101209"
        val formatted = AbhaValidator.autoFormatOnInput(typed14)
        assertEquals("91-4432-8810-1209", formatted)

        // Incremental typing
        assertEquals("9", AbhaValidator.autoFormatOnInput("9"))
        assertEquals("91", AbhaValidator.autoFormatOnInput("91"))
        assertEquals("91-4", AbhaValidator.autoFormatOnInput("914"))
        assertEquals("91-4432", AbhaValidator.autoFormatOnInput("914432"))
        assertEquals("91-4432-8", AbhaValidator.autoFormatOnInput("9144328"))
        assertEquals("91-4432-8810", AbhaValidator.autoFormatOnInput("9144328810"))
        assertEquals("91-4432-8810-1", AbhaValidator.autoFormatOnInput("91443288101"))
        assertEquals("91-4432-8810-1209", AbhaValidator.autoFormatOnInput("91443288101209"))

        // Ignores rogue characters
        assertEquals("91-4432-8810-1209", AbhaValidator.autoFormatOnInput("91.4432/8810-1209"))
    }

    @Test
    fun testPrivacyMasking() {
        val original = "91-4432-8810-1209"
        val masked = AbhaValidator.mask(original)
        assertEquals("91-••••-••••-1209", masked)
        assertFalse(masked.contains("4432"))
        assertFalse(masked.contains("8810"))
    }

    @Test
    fun testRequireValid() {
        val valid = AbhaValidator.requireValid("91-4432-8810-1209")
        assertEquals("91-4432-8810-1209", valid)

        try {
            AbhaValidator.requireValid("invalid-number")
            fail("Expected IllegalArgumentException for invalid-number")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.isNotEmpty())
        }
    }

    @Test
    fun testEncryptionDecryptionAndBlindIndex() {
        val originalAbha = "91-4432-8810-1209"

        // Encrypt
        val record = AbhaSecurityManager.encryptAbha(context, originalAbha)

        // Verifications on ciphertext
        assertTrue(record.encryptedAbha.isNotBlank())
        assertNotEquals(originalAbha, record.encryptedAbha)
        assertFalse(record.encryptedAbha.contains(originalAbha))
        assertEquals("91-••••-••••-1209", record.maskedDisplay)
        assertTrue(record.blindIndexHash.isNotBlank())

        // Decrypt
        val decrypted = AbhaSecurityManager.decryptAbha(context, record.encryptedAbha)
        assertEquals(originalAbha, decrypted)

        // Blind Index determinism
        val blindIndex1 = AbhaSecurityManager.generateBlindIndex(context, originalAbha)
        val blindIndex2 = AbhaSecurityManager.generateBlindIndex(context, "91443288101209")
        assertEquals(blindIndex1, blindIndex2)
        assertEquals(record.blindIndexHash, blindIndex1)

        // Different ABHA gives different blind index
        val differentBlindIndex = AbhaSecurityManager.generateBlindIndex(context, "14-1234-5678-9012")
        assertNotEquals(blindIndex1, differentBlindIndex)
    }
}
