package com.konoz.market.domain

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes rows of text as a minimal .xlsx workbook (one sheet, right-to-left view). Cells that look like plain numbers are
 * stored as numbers so Excel can sum them; everything else is text. No library, no styles: opens in Excel, Google Sheets and LibreOffice.
 */
object XlsxWriter {
    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private val NUMBER = Regex("^-?[0-9]+(\\.[0-9]+)?$")

    fun build(rows: List<List<String>>, sheetName: String = "Report", rightToLeft: Boolean = true): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", CONTENT_TYPES)
            entry("_rels/.rels", ROOT_RELS)
            entry("xl/workbook.xml", workbook(sheetName))
            entry("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            entry("xl/worksheets/sheet1.xml", sheet(rows, rightToLeft))
        }
        return bytes.toByteArray()
    }

    private fun sheet(rows: List<List<String>>, rightToLeft: Boolean): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<sheetViews><sheetView workbookViewId=\"0\"").append(if (rightToLeft) " rightToLeft=\"1\"" else "").append("/></sheetViews>")
        sb.append("<cols><col min=\"1\" max=\"1\" width=\"38\" customWidth=\"1\"/><col min=\"2\" max=\"8\" width=\"16\" customWidth=\"1\"/></cols>")
        sb.append("<sheetData>")
        rows.forEachIndexed { r, cells ->
            if (cells.all { it.isEmpty() }) return@forEachIndexed
            sb.append("<row r=\"").append(r + 1).append("\">")
            cells.forEachIndexed { c, raw ->
                if (raw.isEmpty()) return@forEachIndexed
                val ref = column(c) + (r + 1)
                val text = CsvRows.display(raw)
                if (NUMBER.matches(text)) {
                    sb.append("<c r=\"").append(ref).append("\"><v>").append(text).append("</v></c>")
                } else {
                    sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escape(text)).append("</t></is></c>")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun workbook(name: String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<sheets><sheet name=\"" + escape(name.take(31)) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"

    /** 0 -> A, 25 -> Z, 26 -> AA. */
    internal fun column(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do {
            sb.insert(0, 'A' + (n % 26))
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    /** Escapes XML and drops characters XML 1.0 cannot hold. */
    private fun escape(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private const val CONTENT_TYPES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "</Types>"

    private const val ROOT_RELS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

    private const val WORKBOOK_RELS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
            "</Relationships>"
}
