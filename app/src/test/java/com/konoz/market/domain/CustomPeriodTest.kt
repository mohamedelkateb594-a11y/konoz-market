package com.konoz.market.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CustomPeriodTest {
    private val cairo = TimeZone.getTimeZone("Africa/Cairo")

    private fun utcMidnight(y: Int, m: Int, d: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(y, m, d, 0, 0, 0) }.timeInMillis

    private fun local(y: Int, m: Int, d: Int, h: Int, min: Int, s: Int, ms: Int): Long =
        Calendar.getInstance(cairo).apply { clear(); set(y, m, d, h, min, s); set(Calendar.MILLISECOND, ms) }.timeInMillis

    @Test fun coversWholeLocalDaysBothEndsIncluded() {
        val r = CustomPeriod.rangeOf(utcMidnight(2026, Calendar.NOVEMBER, 3), utcMidnight(2026, Calendar.NOVEMBER, 5), cairo)
        assertEquals(local(2026, Calendar.NOVEMBER, 3, 0, 0, 0, 0), r.from)
        assertEquals(local(2026, Calendar.NOVEMBER, 5, 23, 59, 59, 999), r.to)
    }

    @Test fun reversedPickIsSwapped() {
        val a = CustomPeriod.rangeOf(utcMidnight(2026, Calendar.NOVEMBER, 5), utcMidnight(2026, Calendar.NOVEMBER, 3), cairo)
        val b = CustomPeriod.rangeOf(utcMidnight(2026, Calendar.NOVEMBER, 3), utcMidnight(2026, Calendar.NOVEMBER, 5), cairo)
        assertEquals(b, a)
    }

    @Test fun singleDayIsOneDay() {
        val day = utcMidnight(2026, Calendar.JANUARY, 10)
        val r = CustomPeriod.rangeOf(day, day, cairo)
        assertEquals(local(2026, Calendar.JANUARY, 10, 0, 0, 0, 0), r.from)
        assertEquals(local(2026, Calendar.JANUARY, 10, 23, 59, 59, 999), r.to)
    }
}
