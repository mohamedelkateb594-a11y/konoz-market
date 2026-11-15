package com.konoz.market.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {
    private fun wa(s: String) = PhoneNumbers.toWhatsApp(s)

    @Test fun a_local_egyptian_mobile_gets_the_country_code() {
        assertEquals("201012345678", wa("01012345678"))
        assertEquals("201112345678", wa("01112345678"))
        assertEquals("201212345678", wa("01212345678"))
        assertEquals("201512345678", wa("01512345678"))
    }

    @Test fun other_ways_of_writing_the_same_number_give_the_same_result() {
        val expected = "201012345678"
        assertEquals(expected, wa("+20 101 234 5678"))
        assertEquals(expected, wa("0020 101 234 5678"))
        assertEquals(expected, wa("201012345678"))
        assertEquals(expected, wa("1012345678")) // typed without the leading zero
        assertEquals(expected, wa(" 010-1234-5678 "))
    }

    @Test fun arabic_indic_digits_are_accepted() {
        assertEquals("201012345678", wa("\u0660\u0661\u0660\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668"))
    }

    @Test fun a_number_with_another_country_code_is_kept() {
        assertEquals("966501234567", wa("+966 50 123 4567"))
        assertEquals("966501234567", wa("00966501234567"))
    }

    @Test fun invalid_input_gives_null() {
        assertNull(wa(""))
        assertNull(wa("   "))
        assertNull(wa("abc"))
        assertNull(wa("0123")) // too short
        assertNull(wa("0101234567")) // 10 digits with the zero: one digit missing
        assertNull(wa("010123456789")) // one digit too many
        assertNull(wa("02123456789")) // Cairo landline, not a mobile
        assertNull(wa("01312345678")) // 013 is not a mobile prefix
        assertNull(wa("+1234567890123456")) // longer than 15 digits
    }

    @Test fun isValid_matches_toWhatsApp() {
        assertTrue(PhoneNumbers.isValid("01012345678"))
        assertFalse(PhoneNumbers.isValid("123"))
    }
}
