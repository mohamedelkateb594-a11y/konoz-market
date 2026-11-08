package com.konoz.market.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuantitiesTest {

    @Test fun parse_accepts_decimals_commas_and_arabic_digits() {
        assertEquals(2500L, Quantities.parseMilli("2.5"))
        assertEquals(2500L, Quantities.parseMilli("2,5"))
        assertEquals(12000L, Quantities.parseMilli("\u0661\u0662"))
        assertEquals(250L, Quantities.parseMilli("0.25"))
    }

    @Test fun parse_rejects_invalid_and_negative() {
        assertNull(Quantities.parseMilli(""))
        assertNull(Quantities.parseMilli("abc"))
        assertNull(Quantities.parseMilli("-1"))
    }

    @Test fun cartons_convert_to_pieces() {
        // 2 cartons of 12 = 24 pieces; 2.5 cartons of 12 = 30 pieces
        assertEquals(24000L, Quantities.cartonsToPieceMilli(2000, 12))
        assertEquals(30000L, Quantities.cartonsToPieceMilli(2500, 12))
    }

    @Test fun format_strips_trailing_zeros() {
        assertEquals("10", Quantities.format(10000))
        assertEquals("0.25", Quantities.format(250))
        assertEquals("0", Quantities.format(0))
    }
}
