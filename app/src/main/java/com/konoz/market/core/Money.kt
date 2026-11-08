package com.konoz.market.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Money and quantity helpers.
 *
 * Storage convention (so SQL SUM/ORDER BY stay exact and there is no floating point anywhere):
 *  - Money is stored as Long minor units: 1 EGP = 100 piastres.
 *  - Quantity is stored as Long milli-units: 1 piece = 1000, 0.250 kg = 250.
 * All arithmetic that can produce fractions goes through BigDecimal with HALF_UP rounding.
 */
object Money {
    private val HUNDRED = BigDecimal(100)
    private val THOUSAND = BigDecimal(1000)
    private val TEN_THOUSAND = BigDecimal(10_000)

    fun toMinor(amount: BigDecimal): Long =
        amount.setScale(2, RoundingMode.HALF_UP).multiply(HUNDRED).longValueExact()

    fun fromMinor(minor: Long): BigDecimal = BigDecimal.valueOf(minor, 2)

    /** Parses user input such as "12.5" or "12,50" (also Arabic-Indic digits). Returns null if invalid. */
    fun parseToMinor(text: String): Long? {
        val normalized = normalizeDigits(text).trim().replace(',', '.').replace("\u066B", ".")
        if (normalized.isEmpty()) return null
        return try {
            toMinor(BigDecimal(normalized))
        } catch (e: NumberFormatException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }

    fun format(minor: Long, currency: String = "EGP"): String =
        fromMinor(minor).toPlainString() + " " + currency

    /** Line total = unit price (minor) x quantity (milli) / 1000, rounded HALF_UP to the piastre. */
    fun lineTotal(unitPriceMinor: Long, qtyMilli: Long): Long =
        BigDecimal(unitPriceMinor).multiply(BigDecimal(qtyMilli))
            .divide(THOUSAND, 0, RoundingMode.HALF_UP).longValueExact()

    /** Percentage of an amount, where the rate is in basis points (14% = 1400). */
    fun percentOf(amountMinor: Long, basisPoints: Int): Long =
        BigDecimal(amountMinor).multiply(BigDecimal(basisPoints))
            .divide(TEN_THOUSAND, 0, RoundingMode.HALF_UP).longValueExact()

    /** Cost of a quantity at a unit cost (same math as [lineTotal]). */
    fun costOf(unitCostMinor: Long, qtyMilli: Long): Long = lineTotal(unitCostMinor, qtyMilli)

    /** Profit margin in basis points relative to the sale price; 0 when price is 0. */
    fun marginBasisPoints(costMinor: Long, saleMinor: Long): Int {
        if (saleMinor <= 0L) return 0
        return BigDecimal(saleMinor - costMinor).multiply(TEN_THOUSAND)
            .divide(BigDecimal(saleMinor), 0, RoundingMode.HALF_UP).toInt()
    }

    fun qtyToMilli(qty: BigDecimal): Long =
        qty.setScale(3, RoundingMode.HALF_UP).multiply(THOUSAND).longValueExact()

    fun milliToQty(milli: Long): BigDecimal = BigDecimal.valueOf(milli, 3)

    fun normalizeDigits(s: String): String = buildString {
        for (c in s) {
            when (c) {
                in '\u0660'..'\u0669' -> append('0' + (c - '\u0660'))
                in '\u06F0'..'\u06F9' -> append('0' + (c - '\u06F0'))
                else -> append(c)
            }
        }
    }
}
