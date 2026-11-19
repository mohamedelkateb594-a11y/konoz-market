package com.konoz.market.ui.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.view.View
import androidx.compose.ui.graphics.toArgb
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.core.Quantities
import com.konoz.market.data.entity.PaymentMethod
import com.konoz.market.domain.InvoiceData
import com.konoz.market.ui.shop.ShopProfile
import com.konoz.market.ui.theme.Brand
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/**
 * Draws one invoice as an 80 mm wide receipt (226 pt), as long as its content needs. The same drawing is written to a PDF page
 * or to a PNG (and is the picture the thermal printer prints). Text is laid out with StaticLayout, so Arabic is shaped and
 * right-to-left in an Arabic UI.
 *
 * Look: Modern Blue. A light-blue header card, clean item rows with hairlines, a light-blue totals card with a large blue total.
 * Only light tints and dark ink are used (no dark fills), so a thermal printer prints it as clean black on white.
 */
class InvoiceRenderer(private val context: Context, private val money: (Long) -> String = { Money.format(it) }) {

    class Page(val width: Int, val height: Int, private val painter: (Canvas) -> Unit) {
        fun draw(canvas: Canvas) = painter(canvas)
    }

    fun prepare(data: InvoiceData): Page {
        val cardHeights = mutableListOf<Float>() // measured by the first pass so the second pass can paint a card behind its content
        val height = pass(data, null, cardHeights)
        return Page(WIDTH, height) { canvas -> pass(data, canvas, cardHeights) }
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

    /** One walk over the invoice. With a null canvas it only measures (and records card heights); returns the page height. */
    private fun pass(d: InvoiceData, canvas: Canvas?, cardHeights: MutableList<Float>): Int {
        val rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val direction = if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR

        // Documents are always light, so the light design tokens are used.
        val ink = Brand.TextMain.toArgb()
        val muted = Brand.TextSecondary.toArgb()
        val deep = Brand.PrimaryDeep.toArgb()
        val tint = Brand.Accent.toArgb()
        val lineColor = Brand.OutlineVariantLight.toArgb()
        val accentBar = Brand.Primary.toArgb()

        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = ink }
        val bold = TextPaint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val mutedSmall = TextPaint(body).apply { textSize = 8f; color = muted }
        val title = TextPaint(bold).apply { textSize = 16f; color = deep }
        val numberPaint = TextPaint(bold).apply { color = deep }
        val totalPaint = TextPaint(bold).apply { textSize = 14f; color = deep }
        val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.FILL }
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentBar; style = Paint.Style.FILL }
        val hairPaint = Paint().apply { color = lineColor; strokeWidth = 0.6f }

        var y = MARGIN
        var left = MARGIN
        var width = CONTENT_WIDTH
        var cardIndex = 0

        fun layout(text: String, paint: TextPaint, w: Int, align: Layout.Alignment): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, w)
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
            val l = layout(text, paint, width, align)
            drawAt(l, left)
            y += l.height
        }

        fun row(label: String, value: String, labelPaint: TextPaint = body, valuePaint: TextPaint = labelPaint) {
            val labelWidth = (width * 0.58f).toInt()
            val valueWidth = width - labelWidth
            val labelLayout = layout(label, labelPaint, labelWidth, Layout.Alignment.ALIGN_NORMAL)
            val valueLayout = layout(value, valuePaint, valueWidth, Layout.Alignment.ALIGN_OPPOSITE)
            // The label sits at the start edge (right in Arabic), the amount at the end edge.
            val labelX = if (rtl) left + valueWidth else left
            val valueX = if (rtl) left else left + labelWidth
            drawAt(labelLayout, labelX)
            drawAt(valueLayout, valueX)
            y += max(labelLayout.height, valueLayout.height)
        }

        fun hairline() {
            canvas?.drawLine(left, y + 3f, left + width, y + 3f, hairPaint)
            y += 7f
        }

        /** A rounded light-blue card behind [content]; text inside is inset by CARD_PAD. */
        fun card(content: () -> Unit) {
            val start = y
            val index = cardIndex++
            if (canvas != null) {
                val h = cardHeights.getOrNull(index) ?: 0f
                canvas.drawRoundRect(RectF(MARGIN, start, WIDTH - MARGIN, start + h), CARD_RADIUS, CARD_RADIUS, tintPaint)
            }
            val savedLeft = left
            val savedWidth = width
            left += CARD_PAD
            width -= (CARD_PAD * 2).toInt()
            y += CARD_PAD
            content()
            y += CARD_PAD
            left = savedLeft
            width = savedWidth
            if (canvas == null) cardHeights += (y - start)
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)

        // ---- header ----
        card {
            block(ShopProfile.displayName(context), title, Layout.Alignment.ALIGN_CENTER)
            y += 4f
            row(context.getString(R.string.inv_number_fmt, d.number), dateFormat.format(Date(d.dateMillis)), numberPaint, mutedSmall)
            d.cashier?.let { row(context.getString(R.string.inv_cashier), it, mutedSmall, body) }
            d.customerName?.let { row(context.getString(R.string.inv_customer), it, mutedSmall, body) }
        }
        y += 8f

        // ---- items ----
        d.lines.forEachIndexed { i, line ->
            if (i > 0) hairline()
            block(line.name, bold)
            row(
                context.getString(R.string.inv_qty_price_fmt, Quantities.format(line.qtyMilli), money(line.unitPrice)),
                money(line.total),
                mutedSmall,
                bold,
            )
            if (line.discount > 0L) block(context.getString(R.string.inv_discount_fmt, money(line.discount)), mutedSmall)
            y += 2f
        }
        y += 8f

        // ---- totals ----
        card {
            row(context.getString(R.string.inv_subtotal), money(d.subtotal))
            if (d.invoiceDiscount > 0L) row(context.getString(R.string.inv_invoice_discount), "-" + money(d.invoiceDiscount))
            if (d.taxAmount > 0L) row(context.getString(R.string.inv_tax), money(d.taxAmount))
            y += 3f
            row(context.getString(R.string.inv_total), money(d.total), bold, totalPaint)
        }
        y += 8f

        // ---- payment ----
        for (p in d.payments) row(context.getString(methodLabel(p.method)), money(p.amount), mutedSmall, body)
        row(context.getString(R.string.inv_paid), money(d.paid), bold)
        if (d.changeDue > 0L) row(context.getString(R.string.inv_change), money(d.changeDue), bold, numberPaint)
        d.notes?.let {
            y += 4f
            hairline()
            block(it, mutedSmall)
        }

        // ---- footer ----
        y += 10f
        canvas?.drawRoundRect(RectF(WIDTH / 2f - 14f, y, WIDTH / 2f + 14f, y + 2f), 1f, 1f, barPaint)
        y += 8f
        block(context.getString(R.string.inv_thanks), mutedSmall, Layout.Alignment.ALIGN_CENTER)

        return ceil(y + MARGIN).toInt()
    }

    private fun methodLabel(m: PaymentMethod): Int = when (m) {
        PaymentMethod.CASH -> R.string.inv_pay_cash
        PaymentMethod.CARD -> R.string.inv_pay_card
        PaymentMethod.WALLET -> R.string.inv_pay_wallet
    }

    companion object {
        const val WIDTH = 226 // 80 mm at 72 points per inch
        private const val MARGIN = 12f
        private const val CONTENT_WIDTH = WIDTH - 24
        private const val CARD_PAD = 8f
        private const val CARD_RADIUS = 8f
    }
}
