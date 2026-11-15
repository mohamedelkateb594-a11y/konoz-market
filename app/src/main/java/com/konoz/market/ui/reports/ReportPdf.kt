package com.konoz.market.ui.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.view.View
import com.konoz.market.domain.CsvRows
import java.io.OutputStream

/**
 * Draws report rows (the same rows as the CSV) as an A4 PDF table. Text is laid out with StaticLayout, so Arabic is shaped
 * and the columns run right-to-left in an Arabic UI. A one-cell row is a section title; the first multi-cell row of a block is its header.
 */
class ReportPdf(private val context: Context) {

    fun write(rows: List<List<String>>, out: OutputStream) {
        val rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val direction = if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.BLACK }
        val bold = TextPaint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val title = TextPaint(bold).apply { textSize = 12f }
        val shade = Paint().apply { color = Color.rgb(232, 240, 232) }
        val line = Paint().apply { color = Color.rgb(200, 200, 200); strokeWidth = 0.5f }

        val doc = PdfDocument()
        try {
            var pageNo = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, pageNo).create())
            var y = MARGIN
            var blockStart = true
            var afterTitle = false

            for (raw in rows) {
                val cells = raw.map { CsvRows.display(it) }
                if (cells.all { it.isBlank() }) {
                    y += 10f
                    blockStart = true
                    afterTitle = false
                    continue
                }
                val isTitle = cells.size == 1
                val isHeader = !isTitle && (blockStart || afterTitle)
                val paint = if (isTitle) title else if (isHeader) bold else body
                val widths = columnWidths(cells.size)
                val layouts = cells.mapIndexed { i, text ->
                    StaticLayout.Builder.obtain(text, 0, text.length, paint, (widths[i] - PAD * 2).toInt().coerceAtLeast(20))
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setTextDirection(direction).build()
                }
                val h = layouts.maxOf { it.height } + 6f
                if (y + h > HEIGHT - MARGIN) {
                    doc.finishPage(page)
                    pageNo++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, pageNo).create())
                    y = MARGIN
                }
                val canvas = page.canvas
                if (isHeader) canvas.drawRect(MARGIN, y, WIDTH - MARGIN, y + h, shade)
                drawRow(canvas, layouts, widths, y, rtl)
                if (!isTitle) canvas.drawLine(MARGIN, y + h, WIDTH - MARGIN, y + h, line)
                y += h
                blockStart = false
                afterTitle = isTitle
            }
            doc.finishPage(page)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private fun drawRow(canvas: Canvas, layouts: List<StaticLayout>, widths: List<Float>, y: Float, rtl: Boolean) {
        var x = if (rtl) WIDTH - MARGIN else MARGIN
        layouts.forEachIndexed { i, layout ->
            val left = if (rtl) x - widths[i] else x
            canvas.save()
            canvas.translate(left + PAD, y + 3f)
            layout.draw(canvas)
            canvas.restore()
            x = if (rtl) x - widths[i] else x + widths[i]
        }
    }

    /** One cell: full width; two: 62/38; more: a wide first column then equal columns. */
    private fun columnWidths(count: Int): List<Float> = when {
        count <= 1 -> listOf(CONTENT)
        count == 2 -> listOf(CONTENT * 0.62f, CONTENT * 0.38f)
        else -> {
            val first = CONTENT * 0.34f
            val rest = (CONTENT - first) / (count - 1)
            listOf(first) + List(count - 1) { rest }
        }
    }

    private companion object {
        const val WIDTH = 595
        const val HEIGHT = 842
        const val MARGIN = 36f
        const val PAD = 3f
        const val CONTENT = WIDTH - 2 * MARGIN
    }
}
