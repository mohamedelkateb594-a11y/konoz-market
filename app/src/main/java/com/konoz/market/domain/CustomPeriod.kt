package com.konoz.market.domain

import java.util.Calendar
import java.util.TimeZone

/** Turns the two days picked in a date-range picker into the report period (whole local days, both ends included). */
object CustomPeriod {
    /**
     * [pickedFrom] / [pickedTo] are what Material date pickers return: midnight UTC of the chosen day. They are read as
     * calendar days and re-anchored in [zone] so the period covers the owner's whole local days. A reversed pair is swapped.
     */
    fun rangeOf(pickedFrom: Long, pickedTo: Long, zone: TimeZone = TimeZone.getDefault()): PeriodRange {
        val first = localDay(minOf(pickedFrom, pickedTo), zone)
        val last = localDay(maxOf(pickedFrom, pickedTo), zone)
        return PeriodRange(Periods.rangeOf(PeriodKind.DAY, first, zone).from, Periods.rangeOf(PeriodKind.DAY, last, zone).to)
    }

    private fun localDay(utcMidnight: Long, zone: TimeZone): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
        return Calendar.getInstance(zone).apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
        }.timeInMillis
    }
}
