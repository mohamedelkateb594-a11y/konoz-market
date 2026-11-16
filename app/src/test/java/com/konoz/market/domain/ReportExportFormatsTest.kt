package com.konoz.market.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class ReportExportFormatsTest {
    @Test fun csvRowsReadQuotesCommasAndBlankLines() {
        val rows = CsvRows.parse("\uFEFFa,\"b,c\",\"say \"\"hi\"\"\"\r\n\r\nx,1.50\r\n")
        assertEquals(listOf("a", "b,c", "say \"hi\""), rows[0])
        assertEquals(listOf(""), rows[1])
        assertEquals(listOf("x", "1.50"), rows[2])
        assertEquals(3, rows.size)
    }

    @Test fun csvRowsKeepFieldsWithLineBreaks() {
        val rows = CsvRows.parse("\"two\nlines\",z\r\n")
        assertEquals(listOf("two\nlines", "z"), rows[0])
    }

    @Test fun displayDropsTheFormulaGuardOnly() {
        assertEquals("=SUM", CsvRows.display("'=SUM"))
        assertEquals("'abc", CsvRows.display("'abc"))
        assertEquals("x", CsvRows.display("x"))
    }

    @Test fun columnLetters() {
        assertEquals("A", XlsxWriter.column(0))
        assertEquals("Z", XlsxWriter.column(25))
        assertEquals("AA", XlsxWriter.column(26))
    }

    @Test fun xlsxHasAllPartsNumbersAsNumbersAndEscapedText() {
        val bytes = XlsxWriter.build(listOf(listOf("Tom & <Jerry>", "12.50"), listOf(""), listOf("-3", "abc")))
        val parts = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                parts[e.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertTrue(parts.keys.containsAll(listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/worksheets/sheet1.xml")))
        val sheet = parts.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("Tom &amp; &lt;Jerry&gt;"))
        assertTrue(sheet.contains("<c r=\"B1\"><v>12.50</v></c>"))
        assertTrue(sheet.contains("<c r=\"A3\"><v>-3</v></c>"))
        assertTrue(sheet.contains("rightToLeft=\"1\""))
    }
}
