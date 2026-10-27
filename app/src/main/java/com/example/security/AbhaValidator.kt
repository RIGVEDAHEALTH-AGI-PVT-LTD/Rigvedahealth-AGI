package com.example.security

/**
 * Result of ABHA card number validation
 */
sealed class AbhaValidationResult {
    data class Valid(
        val formattedNumber: String,
        val maskedNumber: String
    ) : AbhaValidationResult()

    data class Invalid(
        val reason: String,
        val partialCleaned: String = ""
    ) : AbhaValidationResult()
}

/**
 * Enforces National Health Authority (NHA) & Ayushman Bharat Digital Mission (ABDM)
 * 14-digit ABHA card number standard: 'XX-XXXX-XXXX-XXXX'.
 */
object AbhaValidator {

    // Strict 14-digit pattern with 3 hyphens separating 2-4-4-4 digits
    val ABHA_FORMAT_REGEX = Regex("^\\d{2}-\\d{4}-\\d{4}-\\d{4}$")
    private val DIGITS_ONLY_REGEX = Regex("^\\d{14}$")

    /**
     * Checks if the given string strictly matches the 'XX-XXXX-XXXX-XXXX' format.
     */
    fun isValid(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        val trimmed = input.trim()
        if (!ABHA_FORMAT_REGEX.matches(trimmed)) return false

        // Check for dummy / all-same digit sequences like 00-0000-0000-0000 or 11-1111-1111-1111
        val digits = trimmed.replace("-", "")
        if (digits.all { it == digits[0] }) return false

        return true
    }

    /**
     * Validates an ABHA card number input and returns detailed validation result.
     */
    fun validate(input: String?): AbhaValidationResult {
        if (input.isNullOrBlank()) {
            return AbhaValidationResult.Invalid("ABHA number cannot be empty")
        }
        val trimmed = input.trim()
        val digitsOnly = trimmed.filter { it.isDigit() }

        if (digitsOnly.length != 14) {
            return AbhaValidationResult.Invalid(
                "ABHA must contain exactly 14 digits (currently ${digitsOnly.length})",
                partialCleaned = digitsOnly
            )
        }

        val formatted = if (trimmed.length == 14 && DIGITS_ONLY_REGEX.matches(trimmed)) {
            formatDigits(trimmed)
        } else {
            trimmed
        }

        if (!ABHA_FORMAT_REGEX.matches(formatted)) {
            return AbhaValidationResult.Invalid(
                "Invalid format. Expected 'XX-XXXX-XXXX-XXXX' pattern (e.g. 91-4432-8810-1209)",
                partialCleaned = digitsOnly
            )
        }

        if (digitsOnly.all { it == digitsOnly[0] }) {
            return AbhaValidationResult.Invalid("Invalid ABHA number: repetitive dummy digits detected")
        }

        return AbhaValidationResult.Valid(
            formattedNumber = formatted,
            maskedNumber = mask(formatted)
        )
    }

    /**
     * Converts a raw digit string (up to 14 digits) into the 'XX-XXXX-XXXX-XXXX' format.
     */
    fun formatDigits(digits: String): String {
        val clean = digits.filter { it.isDigit() }.take(14)
        val sb = StringBuilder()
        for (i in clean.indices) {
            sb.append(clean[i])
            if ((i == 1 || i == 5 || i == 9) && i != clean.lastIndex) {
                sb.append("-")
            }
        }
        return sb.toString()
    }

    /**
     * Auto-formats input dynamically while clinical staff type in real-time.
     */
    fun autoFormatOnInput(input: String): String {
        val digits = input.filter { it.isDigit() }.take(14)
        return formatDigits(digits)
    }

    /**
     * Masks the middle 8 digits for ABDM / DISHA / HIPAA patient privacy compliance.
     * e.g., '91-4432-8810-1209' becomes '91-••••-••••-1209'
     */
    fun mask(formattedAbha: String): String {
        if (!ABHA_FORMAT_REGEX.matches(formattedAbha)) {
            val digits = formattedAbha.filter { it.isDigit() }
            return if (digits.length >= 6) {
                "${digits.take(2)}-••••-••••-${digits.takeLast(4)}"
            } else {
                formattedAbha
            }
        }
        val parts = formattedAbha.split("-")
        return "${parts[0]}-••••-••••-${parts[3]}"
    }

    /**
     * Normalizes and validates. If valid, returns the normalized 'XX-XXXX-XXXX-XXXX',
     * otherwise throws IllegalArgumentException.
     */
    fun requireValid(input: String): String {
        val result = validate(input)
        return when (result) {
            is AbhaValidationResult.Valid -> result.formattedNumber
            is AbhaValidationResult.Invalid -> throw IllegalArgumentException(result.reason)
        }
    }
}
