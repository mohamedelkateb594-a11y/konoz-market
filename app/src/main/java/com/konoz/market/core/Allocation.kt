package com.konoz.market.core

import java.math.BigDecimal
import java.math.RoundingMode

object Allocation {
    /**
     * Splits [total] across [weights] in proportion to the weights. The shares add up EXACTLY to [total]
     * (largest-remainder method, no lost or invented piastres). Returns all zeros when total or the weight sum is 0.
     */
    fun proportional(total: Long, weights: List<Long>): List<Long> {
        val sum = weights.sum()
        if (total == 0L || sum <= 0L) return weights.map { 0L }
        val exact = weights.map {
            BigDecimal(total).multiply(BigDecimal(it)).divide(BigDecimal(sum), 12, RoundingMode.DOWN)
        }
        val shares = exact.map { it.setScale(0, RoundingMode.DOWN).toLong() }.toMutableList()
        var remaining = total - shares.sum()
        val byRemainder = exact.indices.sortedByDescending { exact[it].subtract(BigDecimal(shares[it])) }
        for (i in byRemainder) {
            if (remaining == 0L) break
            shares[i] = shares[i] + 1
            remaining--
        }
        return shares
    }

    /** share x n / of, rounded HALF_UP: the part of [share] that corresponds to n out of [of] units. */
    fun portion(share: Long, n: Long, of: Long): Long {
        if (of == 0L) return 0L
        return BigDecimal(share).multiply(BigDecimal(n)).divide(BigDecimal(of), 0, RoundingMode.HALF_UP).toLong()
    }
}
