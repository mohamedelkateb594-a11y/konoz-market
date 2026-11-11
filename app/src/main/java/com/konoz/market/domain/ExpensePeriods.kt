package com.konoz.market.domain

import java.util.Calendar
import java.util.TimeZone

enum class PeriodKind { DAY, MONTH, YEAR }

/** Inclusive time range in epoch millis, suitable for SQL `BETWEEN from AND to`. */
data class PeriodRange(val from: Long, val to: Long)

object Periods {
    /** The calendar day / month / year (in [zone]) that contains [at]. Uses Calendar so it works on minSdk 24 without desugaring. */
    fun rangeOf(kind: PeriodKind, at: Long, zone: TimeZone = TimeZone.getDefault()): PeriodRange {
        val cal = Calendar.getInstance(zone).apply {
            timeInMillis = at
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (kind) {
            PeriodKind.DAY -> Unit
            PeriodKind.MONTH -> cal.set(Calendar.DAY_OF_MONTH, 1)
            PeriodKind.YEAR -> cal.set(Calendar.DAY_OF_YEAR, 1)
        }
        val from = cal.timeInMillis
        when (kind) {
            PeriodKind.DAY -> cal.add(Calendar.DAY_OF_MONTH, 1)
            PeriodKind.MONTH -> cal.add(Calendar.MONTH, 1)
            PeriodKind.YEAR -> cal.add(Calendar.YEAR, 1)
        }
        return PeriodRange(from, cal.timeInMillis - 1)
    }
}
