package com.konoz.market.core

import java.math.BigDecimal

/** Parsing and conversion helpers for quantities (stored as Long milli-units). */
object Quantities {

    /** Parses "2.5", "2,5" or Arabic-Indic digits to milli-units. Returns null if invalid or negative. */
    fun parseMilli(text: String): Long? {
        val normalized = Money.normalizeDigits(text).trim().replace(',', '.').replace("\u066B", ".")
        if (normalized.isEmpty()) return null
        val value = normalized.toBigDecimalOrNull() ?: return null
        if (value.signum() < 0) return null
        return try {
            Money.qtyToMilli(value)
        } catch (e: ArithmeticException) {
            null
        }
    }

    /** Cartons (in milli-cartons) x pieces per carton = pieces in milli-pieces. 2.5 cartons of 12 = 30 pieces. */
    fun cartonsToPieceMilli(cartonsMilli: Long, piecesPerCarton: Int): Long =
        Math.multiplyExact(cartonsMilli, piecesPerCarton.toLong())

    fun format(milli: Long): String = BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString()
}
