package com.konoz.market.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test fun parse_handles_decimal_comma_and_arabic_digits() {
        assertEquals(1250L, Money.parseToMinor("12.5"))
        assertEquals(1250L, Money.parseToMinor("12,50"))
        assertEquals(1250L, Money.parseToMinor("\u0661\u0662\u066B\u0665"))
        assertNull(Money.parseToMinor("abc"))
        assertNull(Money.parseToMinor(""))
    }

    @Test fun toMinor_rounds_half_up() {
        assertEquals(101L, Money.toMinor(java.math.BigDecimal("1.005")))
        assertEquals(100L, Money.toMinor(java.math.BigDecimal("1.004")))
    }

    @Test fun lineTotal_for_weighted_items() {
        // 12.30 EGP/kg x 2.500 kg = 30.75
        assertEquals(3075L, Money.lineTotal(1230, 2500))
        // 9.99 x 0.333 = 3.32667 -> 3.33
        assertEquals(333L, Money.lineTotal(999, 333))
        // plain piece: 5.00 x 3 pieces
        assertEquals(1500L, Money.lineTotal(500, 3000))
    }

    @Test fun percentOf_uses_basis_points_with_half_up() {
        assertEquals(1407L, Money.percentOf(10050, 1400))  // 14% of 100.50 = 14.07
        assertEquals(140L, Money.percentOf(1001, 1400))    // 14.014 -> 14.01? 1001*0.14=140.14 -> 140
        assertEquals(51L, Money.percentOf(1010, 500))      // 50.5 -> 51 (HALF_UP)
    }

    @Test fun margin_in_basis_points() {
        assertEquals(2500, Money.marginBasisPoints(750, 1000)) // 25%
        assertEquals(0, Money.marginBasisPoints(500, 0))
        assertEquals(-1000, Money.marginBasisPoints(1100, 1000)) // selling below cost
    }

    @Test fun quantity_conversion_roundtrip() {
        assertEquals(250L, Money.qtyToMilli(java.math.BigDecimal("0.25")))
        assertEquals("0.250", Money.milliToQty(250).toPlainString())
    }

    @Test fun format_shows_two_decimals() {
        assertEquals("0.05 EGP", Money.format(5))
        assertEquals("1234.00 EGP", Money.format(123400))
    }
}
