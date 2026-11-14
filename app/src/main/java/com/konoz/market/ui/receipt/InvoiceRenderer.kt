package com.konoz.market.ui.receipt

import android.content.Context
import android.graphics.Bitmap
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
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.core.Quantities
import com.konoz.market.data.entity.PaymentMethod
import com.konoz.market.domain.InvoiceData
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/**
 * Draws one invoice as an 80 mm wide receipt (226 pt), as long as its content needs. The same drawing is written to a PDF page
 * or to a PNG. Text is laid out with StaticLayout, so Arabic is shaped and right-to-left in an Arabic UI.
 */
class InvoiceRenderer(private val context: Context, private val money: (Long) -> String = { Money.format(it) }) {

    class Page(val width: Int, val height: Int, private val painter: (Canvas) -> Unit) {
        fun draw(canvas: Canvas) = painter(canvas)
    }

    fun prepare(data: InvoiceData): Page {
        val height = pass(data, null)
        return Page(WIDTH, height) { canvas -> pass(data, canvas) }
    }

    fun writePdf(page: Page, out: OutputStream) {
        val doc = PdfDocument()
        try {
            val pdfPage = doc.startPage(PdfDocument.PageInfo.Builder(page.width, page.height, 1).create())
            pdfPage.canvas.drawColor(Color.WHITE)
            page.draw(pdfPage.canvas)
            doc.finishPage(pdfPage)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    fun writePng(page: Page, out: OutputStream, scale: Int = 3) {
        val bitmap = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            canvas.scale(scale.toFloat(), scale.toFloat())
            page.draw(canvas)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } finally {
            bitmap.recycle()
        }
    }

    /** One walk over the invoice. With a null canvas it only measures; returns the page height. */
    private fun pass(d: InvoiceData, canvas: Canvas?): Int {
        val rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val direction = if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.BLACK }
        val bold = TextPaint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val title = TextPaint(bold).apply { textSize = 15f }
        val small = TextPaint(body).apply { textSize = 8f; color = Color.rgb(85, 85, 85) }
        val rulePaint = Paint().apply { color = Color.rgb(120, 120, 120); strokeWidth = 0.6f }
        var y = MARGIN

        fun layout(text: String, paint: TextPaint, width: Int, align: Layout.Alignment): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(align).setTextDirection(direction).build()

        fun drawAt(l: StaticLayout, x: Float) {
            canvas?.let {
                it.save()
                it.translate(x, y)
                l.draw(it)
                it.restore()
            }
        }

        fun block(text: String, paint: TextPaint, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) {
            val l = layout(text, paint, CONTENT_WIDTH, align)
            drawAt(l, MARGIN)
            y += l.height
        }

        fun row(label: String, value: String, paint: TextPaint = body) {
            val labelWidth = (CONTENT_WIDTH * 0.58f).toInt()
            val valueWidth = CONTENT_WIDTH - labelWidth
            val labelLayout = layout(label, paint, labelWidth, Layout.Alignment.ALIGN_NORMAL)
            val valueLayout = layout(value, paint, valueWidth, Layout.Alignment.ALIGN_OPPOSITE)
            // The label sits at the start edge (right in Arabic), the amount at the end edge.
            val labelX = if (rtl) MARGIN + valueWidth else MARGIN
            val valueX = if (rtl) MARGIN else MARGIN + labelWidth
            drawAt(labelLayout, labelX)
            drawAt(valueLayout, valueX)
            y += max(labelLayout.height, valueLayout.height)
        }

        fun rule() {
            canvas?.drawLine(MARGIN, y + 3f, WIDTH - MARGIN, y + 3f, rulePaint)
            y += 8f
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)

        block(context.getString(R.string.inv_shop_name), title, Layout.Alignment.ALIGN_CENTER)
        y += 4f
        row(context.getString(R.string.inv_number_fmt, d.number), dateFormat.format(Date(d.dateMillis)), bold)
        d.cashier?.let { row(context.getString(R.string.inv_cashier), it) }
        rule()

        for (line in d.lines) {
            block(line.name, body)
            row(context.getString(R.string.inv_qty_price_fmt, Quantities.format(line.qtyMilli), money(line.unitPrice)), money(line.total))
            if (line.discount > 0L) block(context.getString(R.string.inv_discount_fmt, money(line.discount)), small)
            y += 3f
        }
        rule()

        row(context.getString(R.string.inv_subtotal), money(d.subtotal))
        if (d.invoiceDiscount > 0L) row(context.getString(R.string.inv_invoice_discount), "-" + money(d.invoiceDiscount))
        if (d.taxAmount > 0L) row(context.getString(R.string.inv_tax), money(d.taxAmount))
        row(context.getString(R.string.inv_total), money(d.total), title.also { })
        rule()

        for (p in d.payments) row(context.getString(methodLabel(p.method)), money(p.amount))
        row(context.getString(R.string.inv_paid), money(d.paid), bold)
        if (d.changeDue > 0L) row(context.getString(R.string.inv_change), money(d.changeDue), bold)
        d.notes?.let {
            rule()
            block(it, small)
        }
        rule()
        block(context.getString(R.string.inv_thanks), small, Layout.Alignment.ALIGN_CENTER)

        return ceil(y + MARGIN).toInt()
    }

    private fun methodLabel(m: PaymentMethod): Int = when (m) {
        PaymentMethod.CASH -> R.string.inv_pay_cash
        PaymentMethod.CARD -> R.string.inv_pay_card
        PaymentMethod.WALLET -> R.string.inv_pay_wallet
    }

    companion object {
        const val WIDTH = 226 // 80 mm at 72 points per inch
        private const val MARGIN = 10f
        private const val CONTENT_WIDTH = WIDTH - 20
    }
}
