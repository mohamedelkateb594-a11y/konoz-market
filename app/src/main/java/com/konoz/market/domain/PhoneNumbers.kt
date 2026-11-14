package com.konoz.market.domain

/** Turns what a person types as a customer's phone number into the digits WhatsApp needs (country code first, no plus or zeros). */
object PhoneNumbers {
    private const val EGYPT = "20"

    /**
     * Egyptian numbers may be typed as 01012345678, 1012345678, +20 101 234 5678, 0020 101 234 5678 or 201012345678.
     * A number with another country code (for example +966...) is kept as typed. Arabic-Indic digits are accepted.
     * Returns null when the input cannot be a mobile number (too short, too long, or an Egyptian number that is not a mobile).
     */
    fun toWhatsApp(input: String): String? {
        val trimmed = input.trim()
        val digits = buildString {
            for (c in trimmed) {
                val v = Character.digit(c, 10)
                if (v >= 0) append(v)
            }
        }
        if (digits.isEmpty()) return null
        val international = when {
            trimmed.startsWith("+") -> digits
            digits.startsWith("00") -> digits.drop(2)
            digits.startsWith("0") -> EGYPT + digits.drop(1) // local format: 01xxxxxxxxx
            digits.startsWith(EGYPT) -> digits
            digits.length == 10 && digits.startsWith("1") -> EGYPT + digits // mobile typed without the leading zero
            else -> digits
        }
        if (international.length !in 8..15) return null
        if (international.startsWith(EGYPT) && !EGYPT_MOBILE.matches(international)) return null
        return international
    }

    fun isValid(input: String): Boolean = toWhatsApp(input) != null

    /** 20 + 1 + (0|1|2|5) + 8 digits. */
    private val EGYPT_MOBILE = Regex("^201[0125][0-9]{8}$")
}
