package com.konoz.market.core

import java.math.BigDecimal
import java.math.RoundingMode

object Percent {
    /** "10" -> 1000, "-2.5" -> -250 (basis points). Accepts Arabic digits and a decimal comma. Range -100%..1000%, else null. */
    fun parseBasisPoints(text: String): Int? {
        val normalized = Money.normalizeDigits(text).trim().replace(',', '.').replace("\u066B", ".")
        val value = normalized.toBigDecimalOrNull() ?: return null
        val bp = value.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)
        if (bp < BigDecimal(-10_000) || bp > BigDecimal(100_000)) return null
        return bp.toInt()
    }
}
