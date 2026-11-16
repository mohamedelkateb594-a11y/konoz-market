package com.konoz.market.domain

/** Reads the text [ReportCsv] writes (RFC 4180: quoted fields, doubled quotes, CRLF) back into rows, so PDF and Excel exports reuse the same report content. */
object CsvRows {
    /** A line that is empty in the CSV comes back as one empty cell. A leading byte order mark is ignored. */
    fun parse(text: String): List<List<String>> {
        val s = text.removePrefix("\uFEFF")
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                inQuotes -> {
                    if (ch == '"') {
                        if (i + 1 < s.length && s[i + 1] == '"') {
                            cell.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        cell.append(ch)
                    }
                }
                ch == '"' -> inQuotes = true
                ch == ',' -> {
                    row.add(cell.toString())
                    cell.setLength(0)
                }
                ch == '\r' -> Unit
                ch == '\n' -> {
                    row.add(cell.toString())
                    cell.setLength(0)
                    rows.add(row)
                    row = mutableListOf()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row)
        }
        return rows
    }

    /** The apostrophe [ReportCsv] puts before text that starts with = + - @ protects CSV readers; inside PDF and Excel text it is not needed. */
    fun display(cell: String): String =
        if (cell.length > 1 && cell[0] == '\'' && cell[1] in "=+-@") cell.substring(1) else cell
}
