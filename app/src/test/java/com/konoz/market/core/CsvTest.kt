package com.konoz.market.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CsvTest {
    private val Q = "\u0022"

    @Test fun parses_quotes_delimiters_and_line_breaks_inside_quoted_fields() {
        val text = listOf(
            "a,b",
            "${Q}x,1$Q,${Q}he said ${Q}${Q}hi${Q}${Q}$Q",
            "${Q}line1\nline2$Q,z",
        ).joinToString("\n")
        val rows = Csv.parse(text)
        assertEquals(
            listOf(listOf("a", "b"), listOf("x,1", "he said ${Q}hi$Q"), listOf("line1\nline2", "z")),
            rows,
        )
    }

    @Test fun strips_a_utf8_bom_and_handles_crlf() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), Csv.parse("\uFEFFa,b\r\n1,2\r\n"))
    }

    @Test fun detects_semicolon_and_tab_delimiters() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), Csv.parse("a;b\n1;2"))
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), Csv.parse("a\tb\n1\t2"))
    }

    @Test fun write_quotes_only_when_needed_and_starts_with_a_bom() {
        val out = Csv.write(listOf(listOf("a,b", "c"), listOf("x${Q}y", "")))
        assertEquals("\uFEFF${Q}a,b$Q,c\r\n${Q}x${Q}${Q}y$Q,\r\n", out)
    }

    @Test fun write_then_parse_round_trips_arabic_text() {
        val rows = listOf(listOf("name", "note"), listOf("حليب, طازج", "سطر 1\nسطر 2"))
        assertEquals(rows, Csv.parse(Csv.write(rows)))
    }
}

class PercentTest {
    @Test fun parses_percentages_to_basis_points() {
        assertEquals(1000, Percent.parseBasisPoints("10"))
        assertEquals(-250, Percent.parseBasisPoints("-2.5"))
        assertEquals(250, Percent.parseBasisPoints("2,5"))
        assertEquals(1000, Percent.parseBasisPoints("\u0661\u0660"))
    }

    @Test fun rejects_invalid_and_out_of_range_values() {
        assertNull(Percent.parseBasisPoints("abc"))
        assertNull(Percent.parseBasisPoints(""))
        assertEquals(100_000, Percent.parseBasisPoints("1000"))
        assertNull(Percent.parseBasisPoints("1001"))
        assertEquals(-10_000, Percent.parseBasisPoints("-100"))
        assertNull(Percent.parseBasisPoints("-101"))
    }
}

class ExpiryDatesTest {
    @Test fun parse_format_and_picker_round_trip() {
        val stored = ExpiryDates.parse("2026-12-31")!!
        assertEquals("2026-12-31", ExpiryDates.format(stored))
        // still the same calendar day at the very end of the day
        assertEquals(stored, ExpiryDates.toStored(ExpiryDates.toPickerMillis(stored)))
    }

    @Test fun invalid_dates_are_rejected() {
        assertNull(ExpiryDates.parse("2026-13-40"))
        assertNull(ExpiryDates.parse("hello"))
    }
}
