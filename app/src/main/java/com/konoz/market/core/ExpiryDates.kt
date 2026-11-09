package com.konoz.market.core

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Expiry dates are stored as the END of the expiry day (UTC midnight + 86_399_999 ms), so a product stays
 * sellable for the whole expiry day. The date picker and CSV files speak plain yyyy-MM-dd.
 */
object ExpiryDates {
    const val END_OF_DAY_OFFSET = 86_399_999L
    private const val DAY_MS = 86_400_000L

    private fun formatter() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }

    fun toStored(utcMidnightMillis: Long): Long = utcMidnightMillis + END_OF_DAY_OFFSET

    /** Back to UTC midnight of the expiry day (what the date picker expects). */
    fun toPickerMillis(stored: Long): Long = stored - Math.floorMod(stored, DAY_MS)

    fun format(stored: Long): String = formatter().format(Date(stored))

    /** "2026-12-31" -> stored millis, or null if invalid. */
    fun parse(text: String): Long? = try {
        formatter().parse(text.trim())?.time?.let { toStored(it) }
    } catch (e: ParseException) {
        null
    }
}
