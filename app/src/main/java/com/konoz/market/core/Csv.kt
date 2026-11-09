package com.konoz.market.core

/**
 * Minimal RFC 4180 CSV reader/writer.
 *  - parse: strips a UTF-8 BOM, auto-detects the delimiter (comma, semicolon or tab; Excel in Arabic locales
 *    saves with semicolons), supports quoted fields containing delimiters, quotes and line breaks.
 *  - write: UTF-8 BOM + CRLF so Excel opens Arabic text correctly.
 */
object Csv {
    private const val BOM = "\uFEFF"

    fun parse(input: String): List<List<String>> {
        val text = input.removePrefix(BOM)
        val delimiter = detectDelimiter(text)
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else when {
                c == '"' -> inQuotes = true
                c == delimiter -> {
                    row.add(field.toString())
                    field.setLength(0)
                }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(field.toString())
                    field.setLength(0)
                    rows.add(row)
                    row = ArrayList()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }

    fun write(rows: List<List<String>>, delimiter: Char = ','): String {
        val sb = StringBuilder(BOM)
        for (r in rows) {
            sb.append(r.joinToString(delimiter.toString()) { quote(it, delimiter) }).append("\r\n")
        }
        return sb.toString()
    }

    private fun quote(value: String, delimiter: Char): String {
        val needs = value.any { it == delimiter || it == '"' || it == '\n' || it == '\r' }
        return if (needs) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    private fun detectDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val counts = mapOf(',' to firstLine.count { it == ',' }, ';' to firstLine.count { it == ';' }, '\t' to firstLine.count { it == '\t' })
        val best = counts.maxByOrNull { it.value }
        return if (best == null || best.value == 0) ',' else best.key
    }
}
