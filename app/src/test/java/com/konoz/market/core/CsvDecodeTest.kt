package com.konoz.market.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class CsvDecodeTest {
    private val name = "حليب المراعي"

    @Test fun utf8_bytes_are_read_as_utf8() {
        assertEquals(name, Csv.decode(name.toByteArray(Charsets.UTF_8)))
    }

    @Test fun utf8_with_bom_keeps_the_bom_for_parse_to_strip() {
        val bytes = ("\uFEFF" + name).toByteArray(Charsets.UTF_8)
        assertEquals(name, Csv.parse(Csv.decode(bytes)).single().single())
    }

    @Test fun windows_1256_bytes_from_excel_ansi_csv_are_read_correctly() {
        val bytes = name.toByteArray(Charset.forName("windows-1256"))
        assertEquals(name, Csv.decode(bytes))
    }
}
